package xiamo.morph.client.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xiamo.morph.client.graphics.PlayerRenderHelper;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRenderersMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>
{
    private static final PlayerRenderHelper rendererHelper = new PlayerRenderHelper();

    public PlayerEntityRenderersMixin(EntityRendererFactory.Context ctx, PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
    )
    public void onRenderCall(LivingEntityRenderer<?, ?> renderer, LivingEntity player, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i)
    {
        if (!rendererHelper.onDrawCall(player, f, g, matrixStack, vertexConsumerProvider, i))
        {
            super.render((AbstractClientPlayerEntity) player, f, g, matrixStack, vertexConsumerProvider, i);
        }
    }

    //[FirstPersonModel](https://github.com/tr7zw/FirstPersonModel) compat
    @Inject(method = "renderLeftArm", at = @At(value = "HEAD"))
    public void onLeftArmDrawCall(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci)
    {
        rendererHelper.renderingLeftPart = true;
    }

    @Inject(method = "renderRightArm", at = @At(value = "HEAD"))
    public void onRightArmDrawCall(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci)
    {
        rendererHelper.renderingLeftPart = false;
    }

    @Inject(method = "renderArm", at = @At(value = "HEAD"), cancellable = true)
    public void onArmDrawCall(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci)
    {
        if (rendererHelper.onArmDrawCall(matrices, vertexConsumers, light, player, arm, sleeve))
            ci.cancel();
    }
}
