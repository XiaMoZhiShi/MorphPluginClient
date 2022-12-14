package xiamomc.morph.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Bindables.Bindable;
import xiamomc.morph.client.config.ModConfigData;
import xiamomc.morph.network.commands.C2S.AbstractC2SCommand;
import xiamomc.morph.network.commands.C2S.C2SInitialCommand;
import xiamomc.morph.network.commands.C2S.C2SOptionCommand;

import java.nio.charset.StandardCharsets;

public class ServerHandler extends MorphClientObject
{
    private final MorphClient client;

    public ServerHandler(MorphClient client)
    {
        this.client = client;
    }

    //region Common

    private static final String morphNameSpace = "morphplugin";

    public static Identifier initializeChannelIdentifier = new Identifier(morphNameSpace, "init");
    public static Identifier versionChannelIdentifier = new Identifier(morphNameSpace, "version");
    public static Identifier commandChannelIdentifier = new Identifier(morphNameSpace, "commands");

    @Resolved
    private ClientMorphManager morphManager;

    @Resolved
    private DisguiseSyncer syncer;

    @Resolved
    private ModConfigData config;

    @Resolved
    private ClientSkillHandler skillHandler;

    //endregion

    //region Network

    public boolean serverReady()
    {
        return serverReady.get();
    }

    private int serverVersion = -1;
    private final int clientVersion = 3;

    public int getServerVersion()
    {
        return serverVersion;
    }

    public boolean serverApiMatch()
    {
        return this.getServerVersion() == clientVersion;
    }

    public int getClientVersion()
    {
        return clientVersion;
    }

    private String readStringfromByte(ByteBuf buf)
    {
        return buf.resetReaderIndex().readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
    }

    private PacketByteBuf fromString(String str)
    {
        var packet = PacketByteBufs.create();

        packet.writeCharSequence(str, StandardCharsets.UTF_8);
        return packet;
    }

    public void sendCommand(AbstractC2SCommand<?> command)
    {
        var cmd = command.buildCommand();
        if (cmd == null || cmd.isEmpty() || cmd.isBlank()) return;

        cmd = cmd.trim();

        ClientPlayNetworking.send(commandChannelIdentifier, fromString(cmd));
    }

    public final Bindable<Boolean> serverReady = new Bindable<>(false);
    private boolean handshakeReceived;
    private boolean apiVersionChecked;

    public void resetServerStatus()
    {
        handshakeReceived = false;
        apiVersionChecked = false;

        morphManager.reset();
        updateServerStatus();
    }

    private void updateServerStatus()
    {
        serverReady.set(handshakeReceived && apiVersionChecked);
    }

    public void initializeClientData()
    {
        this.resetServerStatus();

        ClientPlayNetworking.send(initializeChannelIdentifier, PacketByteBufs.create());
    }

    private boolean networkInitialized;

    public void initializeNetwork()
    {
        if (networkInitialized)
            throw new RuntimeException("??????????????????????????????");

        ClientPlayConnectionEvents.INIT.register((handler, client) ->
        {
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
        {
            initializeClientData();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            resetServerStatus();
        });

        //???????????????
        ClientPlayNetworking.registerGlobalReceiver(initializeChannelIdentifier, (client, handler, buf, responseSender) ->
        {
            if (this.readStringfromByte(buf).equalsIgnoreCase("no"))
            {
                logger.error("????????????????????????????????????");
                return;
            }

            handshakeReceived = true;
            updateServerStatus();

            ClientPlayNetworking.send(versionChannelIdentifier, fromString("" + clientVersion));
            sendCommand(new C2SInitialCommand());
            sendCommand(new C2SOptionCommand(C2SOptionCommand.ClientOptions.CLIENTVIEW).setValue(config.allowClientView));
            sendCommand(new C2SOptionCommand(C2SOptionCommand.ClientOptions.HUD).setValue(config.displayDisguiseOnHud));
        });

        ClientPlayNetworking.registerGlobalReceiver(versionChannelIdentifier, (client, handler, buf, responseSender) ->
        {
            try
            {
                serverVersion = buf.readInt();
                apiVersionChecked = true;
                updateServerStatus();
            }
            catch (Exception e)
            {
                logger.error("?????????????????????API?????????" + e.getMessage());
                e.printStackTrace();
            }

            logger.info("?????????API?????????" + serverVersion);
        });

        ClientPlayNetworking.registerGlobalReceiver(commandChannelIdentifier, (client, handler, buf, responseSender) ->
        {
            var str = readStringfromByte(buf).split(" ", 3);

            if (!serverReady.get() && (str.length != 1 || !str[0].equals("reauth")))
            {
                if (config.verbosePackets)
                    logger.warn("????????????????????????????????????????????????" + readStringfromByte(buf) + "??????????????????????????????");

                return;
            }

            try
            {
                if (config.verbosePackets)
                    logger.info("???????????????????????????" + readStringfromByte(buf));

                if (str.length < 1) return;

                var baseName = str[0];

                switch (baseName)
                {
                    case "swap" ->
                    {
                        morphManager.swapHand();
                    }
                    case "query" ->
                    {
                        if (str.length < 2) return;

                        var subCmdName = str[1];

                        var diff = new ObjectArrayList<>(str[2].split(" "));
                        diff.removeIf(String::isEmpty);

                        switch (subCmdName) {
                            case "add" ->
                            {
                                morphManager.addDisguises(diff);
                            }
                            case "remove" ->
                            {
                                morphManager.removeDisguises(diff);
                            }
                            case "set" ->
                            {
                                morphManager.setDisguises(diff);
                            }
                            default -> logger.warn("?????????Query?????????" + subCmdName);
                        }
                    }
                    case "set" ->
                    {
                        if (str.length < 2) return;

                        var subCmdName = str[1];

                        switch (subCmdName)
                        {
                            case "cd" ->
                            {
                                if (str.length < 3) return;

                                long val = -1;

                                try
                                {
                                    val = Math.max(0L, Long.parseLong(str[2]));
                                }
                                catch (Throwable ignored)
                                {
                                }

                                skillHandler.setSkillCooldown(val);
                            }
                            case "toggleself" ->
                            {
                                if (str.length < 3) return;

                                var val = Boolean.parseBoolean(str[2]);

                                morphManager.selfVisibleToggled.set(val);
                            }
                            case "selfview" ->
                            {
                                if (str.length < 3) return;

                                var identifier = str[2];

                                morphManager.selfViewIdentifier.set(identifier);
                            }
                            case "fake_equip" ->
                            {
                                if (str.length < 3) return;

                                var value = Boolean.valueOf(str[2]);

                                morphManager.equipOverriden.set(value);
                            }
                            case "equip" ->
                            {
                                if (str.length < 3) return;

                                var dat = str[2].split(" ", 2);

                                if (dat.length != 2) return;
                                var currentMob = DisguiseSyncer.currentEntity.get();

                                if (currentMob == null) return;

                                var stack = jsonToStack(dat[1]);

                                if (stack == null) return;

                                switch (dat[0])
                                {
                                    case "mainhand" -> morphManager.setEquip(EquipmentSlot.MAINHAND, stack);
                                    case "off_hand" -> morphManager.setEquip(EquipmentSlot.OFFHAND, stack);

                                    case "helmet" -> morphManager.setEquip(EquipmentSlot.HEAD, stack);
                                    case "chestplate" -> morphManager.setEquip(EquipmentSlot.CHEST, stack);
                                    case "leggings" -> morphManager.setEquip(EquipmentSlot.LEGS, stack);
                                    case "boots" -> morphManager.setEquip(EquipmentSlot.FEET, stack);
                                }
                            }
                            case "nbt" ->
                            {
                                if (str.length < 3) return;

                                var nbt = StringNbtReader.parse(str[2].replace("\\u003d", "="));

                                morphManager.currentNbtCompound.set(nbt);
                            }
                            case "profile" ->
                            {
                                if (str.length < 3) return;

                                var nbt = StringNbtReader.parse(str[2]);
                                var profile = NbtHelper.toGameProfile(nbt);

                                if (profile != null)
                                    this.client.schedule(() -> syncer.updateSkin(profile));
                            }
                            case "sneaking" ->
                            {
                                if (str.length < 3) return;

                                serverSideSneaking = Boolean.valueOf(str[2]);
                            }
                        }
                    }
                    case "reauth" ->
                    {
                        initializeClientData();
                    }
                    case "unauth" ->
                    {
                        resetServerStatus();
                    }
                    case "current" ->
                    {
                        var val = str.length == 2 ? str[1] : null;
                        morphManager.setCurrent(val);
                    }
                    case "deny" ->
                    {
                        if (str.length < 2) return;

                        var subCmdName = str[1];

                        if (subCmdName.equals("morph"))
                        {
                            morphManager.selectedIdentifier.triggerChange();
                            morphManager.currentIdentifier.triggerChange();
                        }
                        else
                            logger.warn("?????????Deny?????????" + subCmdName);
                    }
                    default -> logger.warn("???????????????????????????" + baseName);
                }
            }
            catch (Exception e)
            {
                logger.error("???????????????" + e.getMessage());
                e.printStackTrace();
            }
        });

        networkInitialized = true;
    }

    public static Boolean serverSideSneaking;

    @Nullable
    private ItemStack jsonToStack(String rawJson)
    {
        var item = ItemStack.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(rawJson));

        if (item.result().isPresent())
            return item.result().get().getFirst();

        return null;
    }

    //endregion Network
}
