package mods.betterfoliage.client.render

import mods.betterfoliage.BetterFoliageMod
import mods.betterfoliage.client.Client
import mods.betterfoliage.client.config.Config
import mods.betterfoliage.client.integration.ShadersModIntegration
import mods.octarinecore.client.render.*
import mods.octarinecore.common.Int3
import mods.octarinecore.common.Rotation
import net.minecraft.block.material.Material
import net.minecraft.client.renderer.BlockRendererDispatcher
import net.minecraft.client.renderer.VertexBuffer
import net.minecraft.util.BlockRenderLayer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.apache.logging.log4j.Level.INFO

@SideOnly(Side.CLIENT)
class RenderAlgae : AbstractBlockRenderingHandler(BetterFoliageMod.MOD_ID) {

    val noise = simplexNoise()

    val algaeIcons = iconSet(BetterFoliageMod.LEGACY_DOMAIN, "blocks/better_algae_%d")
    val algaeModels = modelSet(64, RenderGrass.grassTopQuads(Config.algae.heightMin, Config.algae.heightMax))

    override fun afterStitch() {
        Client.log(INFO, "Registered ${algaeIcons.num} algae textures")
    }

    override fun isEligible(ctx: BlockContext) =
        Config.enabled && Config.algae.enabled &&
        ctx.cameraDistance < Config.algae.distance &&
        ctx.blockState(up2).material == Material.WATER &&
        ctx.blockState(up1).material == Material.WATER &&
        Config.blocks.dirt.matchesClass(ctx.block) &&
        ctx.biomeId in Config.algae.biomes &&
        noise[ctx.pos] < Config.algae.population

    override fun render(ctx: BlockContext, dispatcher: BlockRendererDispatcher, renderer: VertexBuffer, layer: BlockRenderLayer): Boolean {
        val baseRender = renderWorldBlockBase(ctx, dispatcher, renderer, layer)
        if (!layer.isCutout) return baseRender

        modelRenderer.updateShading(Int3.zero, allFaces)

        val rand = ctx.semiRandomArray(3)

        ShadersModIntegration.grass(renderer, Config.algae.shaderWind) {
            modelRenderer.render(
                renderer,
                algaeModels[rand[2]],
                Rotation.identity,
                icon = { _, qi, _ -> algaeIcons[rand[qi and 1]]!! },
                postProcess = noPost
            )
        }
        return true
    }
}