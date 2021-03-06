package mods.betterfoliage.client.texture

import mods.betterfoliage.BetterFoliageMod
import mods.betterfoliage.client.Client
import mods.betterfoliage.client.config.Config
import mods.betterfoliage.client.integration.OptifineCTM
import mods.octarinecore.client.render.BlockContext
import mods.octarinecore.client.resource.*
import mods.octarinecore.common.Int3
import mods.octarinecore.common.config.ConfigurableBlockMatcher
import mods.octarinecore.common.config.ModelTextureList
import mods.octarinecore.findFirst
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger

const val defaultLeafColor = 0

/** Rendering-related information for a leaf block. */
class LeafInfo(
    /** The generated round leaf texture. */
    val roundLeafTexture: TextureAtlasSprite,

    /** Type of the leaf block (configurable by user). */
    val leafType: String,

    /** Average color of the round leaf texture. */
    val averageColor: Int = roundLeafTexture.averageColor ?: defaultLeafColor
) {
    /** [IconSet] of the textures to use for leaf particles emitted from this block. */
    val particleTextures: IconSet? get() = LeafRegistry.particles[leafType]
}

interface ILeafRegistry {
    operator fun get(state: IBlockState, rand: Int): LeafInfo?
    operator fun get(state: IBlockState, world: IBlockAccess, pos: BlockPos, face: EnumFacing, rand: Int): LeafInfo?
}

/** Collects and manages rendering-related information for grass blocks. */
object LeafRegistry : ILeafRegistry {
    val subRegistries: MutableList<ILeafRegistry> = mutableListOf(StandardLeafSupport)
    val typeMappings = TextureMatcher()
    val particles = hashMapOf<String, IconSet>()

    init { MinecraftForge.EVENT_BUS.register(this) }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun handlePreStitch(event: TextureStitchEvent.Pre) {
        particles.clear()
        typeMappings.loadMappings(ResourceLocation(BetterFoliageMod.DOMAIN, "leafTextureMappings.cfg"))
    }

    override fun get(state: IBlockState, world: IBlockAccess, pos: BlockPos, face: EnumFacing, rand: Int) =
        subRegistries.findFirst { it.get(state, world, pos, face, rand) }

    operator fun get(ctx: BlockContext, face: EnumFacing) = get(ctx.blockState(Int3.zero), ctx.world!!, ctx.pos, face, ctx.random(0))

    override fun get(state: IBlockState, rand: Int) = subRegistries.findFirst { it[state, rand] }

    fun getParticleType(texture: TextureAtlasSprite, atlas: TextureMap): String {
        var leafType = typeMappings.getType(texture) ?: "default"
        if (leafType !in particles.keys) {
            val particleSet = IconSet("betterfoliage", "blocks/falling_leaf_${leafType}_%d")
            particleSet.onStitch(atlas)
            if (particleSet.num == 0) {
                Client.log(Level.WARN, "Leaf particle textures not found for leaf type: $leafType")
                leafType = "default"
            } else {
                particles.put(leafType, particleSet)
            }
        }
        return leafType
    }
}


@SideOnly(Side.CLIENT)
object StandardLeafSupport :
    TextureListModelProcessor<TextureAtlasSprite>,
    TextureMediatedRegistry<List<String>, LeafInfo>,
    ILeafRegistry
{

    init { MinecraftForge.EVENT_BUS.register(this) }

    override val logName = "StandardLeafSupport"
    override val matchClasses: ConfigurableBlockMatcher get() = Config.blocks.leavesClasses
    override val modelTextures: List<ModelTextureList> get() = Config.blocks.leavesModels.list
    override val logger: Logger? get() = BetterFoliageMod.logDetail

    override var variants = mutableMapOf<IBlockState, MutableList<ModelVariant>>()
    override var variantToKey = mutableMapOf<ModelVariant, List<String>>()
    override var variantToValue = mapOf<ModelVariant, TextureAtlasSprite>()
    override var textureToValue = mutableMapOf<TextureAtlasSprite, LeafInfo>()

    override fun get(state: IBlockState, world: IBlockAccess, pos: BlockPos, face: EnumFacing, rand: Int): LeafInfo? {
        val variant = getVariant(state, rand) ?: return null
        val baseTexture = variantToValue[variant] ?: return null
        return textureToValue[OptifineCTM.override(baseTexture, world, pos, face)] ?: textureToValue[baseTexture]
    }

    override fun get(state: IBlockState, rand: Int): LeafInfo? {
        val variant = getVariant(state, rand) ?: return null
        return variantToValue[variant].let { if (it == null) null else textureToValue[it] }
    }

    override fun processStitch(variant: ModelVariant, key: List<String>, atlas: TextureMap) = atlas[key[0]]

    override fun processTexture(variants: List<ModelVariant>, texture: TextureAtlasSprite, atlas: TextureMap) {
        logger?.log(Level.DEBUG, "$logName: leaf texture   ${texture.iconName}")
        logger?.log(Level.DEBUG, "$logName:      #variants ${variants.size}")
        logger?.log(Level.DEBUG, "$logName:      #states   ${variants.distinctBy { it.state }.size}")
        registerLeaf(texture, atlas)
        OptifineCTM.getAllCTM(variants.map { it.state }, texture).forEach {
            logger?.log(Level.DEBUG, "$logName:        CTM ${texture.iconName}")
            registerLeaf(it, atlas)
        }
    }

    fun registerLeaf(texture: TextureAtlasSprite, atlas: TextureMap) {
        var leafType = LeafRegistry.typeMappings.getType(texture) ?: "default"
        logger?.log(Level.DEBUG, "$logName:      particle $leafType")
        val generated = atlas.registerSprite(
            Client.genLeaves.generatedResource(texture.iconName, "type" to leafType)
        )
        textureToValue[texture] = LeafInfo(generated, LeafRegistry.getParticleType(texture, atlas))
    }

}

