package xiamomc.morph.client.screens.disguise;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import xiamomc.morph.client.*;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Bindables.Bindable;
import xiamomc.morph.client.graphics.InventoryRenderHelper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StringWidget extends ElementListWidget.Entry<StringWidget> implements Comparable<StringWidget>
{
    private TextWidget field;

    private String identifier = "???";

    public String getIdentifier()
    {
        return identifier;
    }

    @Override
    public List<? extends Selectable> selectableChildren()
    {
        return children;
    }

    @Override
    public List<? extends Element> children()
    {
        return children;
    }
    private final List<TextWidget> children = new ObjectArrayList<>();

    public StringWidget(String name)
    {
        initFields(name);
    }

    public void clearChildren()
    {
        children.forEach(TextWidget::dispose);
    }

    private void initFields(String name)
    {
        this.identifier = name;
        children.add(field = new TextWidget(0, 0, 180, 20, name));
    }

    @Override
    public void render(MatrixStack matrices, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta)
    {
        field.screenSpaceY = y;
        field.screenSpaceX = x;
        field.render(matrices, mouseX, mouseY, tickDelta);
    }

    @Override
    public int compareTo(@NotNull StringWidget stringWidget)
    {
        return identifier.compareTo(stringWidget.identifier);
    }

    private static class TextWidget extends MorphClientObject implements Selectable, Drawable, Element
    {
        private final String identifier;
        private Text display;

        int screenSpaceY = 0;
        int screenSpaceX = 0;

        int width = 0;
        int height = 0;

        private LivingEntity entity;
        private int entitySize;
        private int entityYOffset;

        @Resolved(shouldSolveImmediately = true)
        private ClientMorphManager manager;

        private void dispose()
        {
            currentIdentifier = null;
            selectedIdentifier = null;
        }

        private Bindable<String> currentIdentifier = new Bindable<>();
        private Bindable<String> selectedIdentifier = new Bindable<>();

        public TextWidget(int screenSpaceX, int screenSpaceY, int width, int height, String identifier)
        {
            this.identifier = identifier;
            this.display = Text.translatable("gui.morphclient.loading")
                    .formatted(Formatting.ITALIC, Formatting.GRAY);

            this.screenSpaceX = screenSpaceX;
            this.screenSpaceY = screenSpaceY;

            this.width = width;
            this.height = height;

            selectedIdentifier.bindTo(manager.selectedIdentifier);
            currentIdentifier.bindTo(manager.currentIdentifier);

            if (identifier.equals(currentIdentifier.get()) || identifier.equals("morph:unmorph"))
                setupEntity(identifier);

            selectedIdentifier.onValueChanged((o, n) ->
            {
                if (!identifier.equals(n) && focusType != FocusType.CURRENT && focusType != FocusType.WAITING)
                    focusType = FocusType.NONE;
            }, true);

            currentIdentifier.onValueChanged((o, n) ->
            {
                if (identifier.equals(n))
                {
                    focusType = FocusType.CURRENT;

                    if (entity != null && entity.isRemoved()) entity = EntityCache.getEntity(n);
                }
                else focusType = FocusType.NONE;
            }, true);
        }

        private void setupEntity(String identifier)
        {
            try
            {
                LivingEntity living = EntityCache.getEntity(identifier);

                if (living == null)
                {
                    LivingEntity entity = null;

                    if (identifier.equals("morph:unmorph"))
                    {
                        entity = MinecraftClient.getInstance().player;
                    }
                    else if (identifier.startsWith("player:"))
                    {
                        var nameSplited = identifier.split(":", 2);

                        if (nameSplited.length == 2)
                        {
                            entity = new MorphLocalPlayer(MinecraftClient.getInstance().world,
                                    new GameProfile(UUID.randomUUID(), nameSplited[1]));
                        }
                    }

                    //????????????ID???????????????
                    if (entity == null)
                    {
                        this.display = Text.literal(identifier);
                        return;
                    }

                    living = entity;
                }

                this.entity = living;
                this.display = entity.getDisplayName();

                entitySize = getEntitySize(entity);
                entityYOffset = getEntityYOffset(entity);

                if (entity.getType() == EntityType.MAGMA_CUBE)
                    ((MagmaCubeEntity) living).setSize(4, false);
            }
            catch (Exception e)
            {
                logger.error(e.getMessage());
                e.printStackTrace();
            }
        }

        private int getEntityYOffset(LivingEntity entity)
        {
            var type = Registries.ENTITY_TYPE.getId(entity.getType());

            return switch (type.toString())
                    {
                        case "minecraft:squid", "minecraft:glow_squid" -> -6;
                        default -> 0;
                    };
        }

        private int getEntitySize(LivingEntity entity)
        {
            var type = Registries.ENTITY_TYPE.getId(entity.getType());

            return switch (type.toString())
                    {
                        case "minecraft:ender_dragon" -> 2;
                        case "minecraft:squid", "minecraft:glow_squid" -> 10;
                        case "minecraft:magma_cube" ->
                        {
                            ((MagmaCubeEntity) entity).setSize(4, false);
                            yield 8;
                        }
                        default ->
                        {
                            var size = (int) (15 / Math.max(entity.getHeight(), entity.getWidth()));
                            size = Math.max(1, size);

                            yield size;
                        }
                    };
        }

        private final static TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        private final InventoryRenderHelper inventoryRenderHelper = InventoryRenderHelper.getInstance();

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta)
        {
            if (entity == null)
                CompletableFuture.runAsync(() -> this.setupEntity(identifier));

            if (focusType != FocusType.NONE)
            {
                var bordercolor = switch (focusType)
                        {
                            case SELECTED -> 0xffffaa00;
                            case CURRENT -> 0xffabcdef;
                            case WAITING -> 0xff694400;
                            default -> 0x00000000;
                        };

                DrawableHelper.fill(matrices, screenSpaceX, screenSpaceY,
                        screenSpaceX + width, screenSpaceY + height, bordercolor);

                DrawableHelper.fill(matrices, screenSpaceX + 1, screenSpaceY + 1,
                        screenSpaceX + width - 1, screenSpaceY + height - 1,
                        0xff333333);
            }

            textRenderer.draw(matrices, display,
                    screenSpaceX + 5, screenSpaceY + ((height - textRenderer.fontHeight) / 2f), 0xffffffff);

            try
            {
                if (entity != null && allowER)
                {
                    var x = screenSpaceX + width - 5;
                    var y = screenSpaceY + height - 2 + entityYOffset;
                    var mX = 30;
                    var mY = 0;

                    if (focusType == FocusType.CURRENT || entity == MinecraftClient.getInstance().player)
                        entitySize = this.getEntitySize(entity);

                    if (focusType != FocusType.NONE)
                    {
                        mX = x - mouseX;
                        mY = y -mouseY;
                    }

                    if (entity == MinecraftClient.getInstance().player)
                        inventoryRenderHelper.onRenderCall(x, y, entitySize, mX, mY);
                    else
                        InventoryScreen.drawEntity(x, y, entitySize, mX, mY, entity);
                }
            }
            catch (Exception e)
            {
                allowER = false;
                LoggerFactory.getLogger("morph").error(e.getMessage());
                e.printStackTrace();
            }
        }

        private boolean allowER = true;

        @Override
        public SelectionType getType()
        {
            return (focusType == FocusType.CURRENT ? SelectionType.FOCUSED : SelectionType.NONE);
        }

        private FocusType focusType;

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button)
        {
            if (button == 0)
            {
                var lastFocusType = focusType;

                switch (lastFocusType)
                {
                    case SELECTED ->
                    {
                        focusType = FocusType.WAITING;
                        MorphClient.getInstance().sendMorphCommand(this.identifier);
                    }

                    case CURRENT ->
                    {
                        if (manager.selectedIdentifier.get() != null)
                            manager.selectedIdentifier.set(null);
                    }

                    case WAITING ->
                    {
                    }

                    default ->
                    {
                        if (mouseX < this.screenSpaceX + width && mouseX > this.screenSpaceX
                                && mouseY < this.screenSpaceY + height && mouseY > this.screenSpaceY)
                        {
                            manager.selectedIdentifier.set(this.identifier);
                            focusType = FocusType.SELECTED;
                        }
                    }
                }

                return true;
            }
            else if (button == 1) //Selected + ?????? -> ????????????
            {
                if (focusType == FocusType.SELECTED)
                {
                    manager.selectedIdentifier.set(null);
                }
                else if (focusType == FocusType.CURRENT)
                {
                    focusType = FocusType.WAITING;
                    MorphClient.getInstance().sendMorphCommand(null);
                }
            }

            return Element.super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void appendNarrations(NarrationMessageBuilder builder) { }

        private enum FocusType
        {
            NONE,
            SELECTED,
            WAITING,
            CURRENT
        }
    }
}
