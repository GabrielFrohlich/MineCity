package br.com.gamemods.minecity.sponge;

import br.com.gamemods.minecity.MineCity;
import br.com.gamemods.minecity.MineCityConfig;
import br.com.gamemods.minecity.api.command.LegacyFormat;
import br.com.gamemods.minecity.api.command.Message;
import br.com.gamemods.minecity.api.permission.PermissionFlag;
import br.com.gamemods.minecity.api.permission.SimpleFlagHolder;
import br.com.gamemods.minecity.datasource.api.DataSourceException;
import br.com.gamemods.minecity.economy.EconomyLayer;
import br.com.gamemods.minecity.permission.PermissionLayer;
import br.com.gamemods.minecity.reactive.ReactiveLayer;
import br.com.gamemods.minecity.sponge.cmd.SpongeRootCommand;
import br.com.gamemods.minecity.sponge.cmd.SpongeTransformer;
import br.com.gamemods.minecity.sponge.core.mixed.MixedChunk;
import br.com.gamemods.minecity.sponge.data.manipulator.reactive.SpongeManipulator;
import com.flowpowered.math.vector.Vector3i;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.manipulator.mutable.item.BlockItemData;
import org.spongepowered.api.data.manipulator.mutable.item.DurabilityData;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.state.GameAboutToStartServerEvent;
import org.spongepowered.api.event.game.state.GameConstructionEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStoppedServerEvent;
import org.spongepowered.api.event.world.LoadWorldEvent;
import org.spongepowered.api.event.world.UnloadWorldEvent;
import org.spongepowered.api.event.world.chunk.LoadChunkEvent;
import org.spongepowered.api.event.world.chunk.UnloadChunkEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.world.Chunk;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Plugin(id="minecity", name="MineCity", authors = "joserobjr")
public class MineCitySpongePlugin
{
    @Inject
    private Logger logger;

    @Inject
    @DefaultConfig(sharedRoot = true)
    private ConfigurationLoader<CommentedConfigurationNode> configManager;

    private MineCityConfig config;
    private SpongeTransformer transformer;
    private String lang;
    private MineCitySponge sponge;
    private Task reloadTask;

    @Listener
    public void onGameConstruct(GameConstructionEvent event)
    {
        try
        {
            LegacyFormat.BLACK.server = TextColors.BLACK;
            LegacyFormat.DARK_BLUE.server = TextColors.DARK_BLUE;
            LegacyFormat.DARK_GREEN.server = TextColors.DARK_GREEN;
            LegacyFormat.DARK_AQUA.server = TextColors.DARK_AQUA;
            LegacyFormat.DARK_RED.server = TextColors.DARK_RED;
            LegacyFormat.DARK_PURPLE.server = TextColors.DARK_PURPLE;
            LegacyFormat.GOLD.server = TextColors.GOLD;
            LegacyFormat.GRAY.server = TextColors.GRAY;
            LegacyFormat.DARK_GRAY.server = TextColors.DARK_GRAY;
            LegacyFormat.BLUE.server = TextColors.BLUE;
            LegacyFormat.GREEN.server = TextColors.GREEN;
            LegacyFormat.AQUA.server = TextColors.AQUA;
            LegacyFormat.RED.server = TextColors.RED;
            LegacyFormat.LIGHT_PURPLE.server = TextColors.LIGHT_PURPLE;
            LegacyFormat.YELLOW.server = TextColors.YELLOW;
            LegacyFormat.WHITE.server = TextColors.WHITE;
            LegacyFormat.RESET.server = TextColors.RESET;
            LegacyFormat.MAGIC.server = TextStyles.OBFUSCATED;
            LegacyFormat.BOLD.server = TextStyles.BOLD;
            LegacyFormat.STRIKE.server = TextStyles.STRIKETHROUGH;
            LegacyFormat.UNDERLINE.server = TextStyles.UNDERLINE;
            LegacyFormat.ITALIC.server = TextStyles.ITALIC;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Sponge.getServer().shutdown();
            throw e;
        }
    }

    private double getOrSetDouble(ConfigurationNode node, double def)
    {
        return getOrSet(node, def, node::getDouble);
    }

    private int getOrSetInt(ConfigurationNode node, int def)
    {
        return getOrSet(node, def, node::getInt);
    }

    private String getOrSetStr(ConfigurationNode node, String def)
    {
        return getOrSet(node, def, node::getString);
    }

    private boolean getOrSetBool(ConfigurationNode node, boolean def)
    {
        return getOrSet(node, def, node::getBoolean);
    }

    private <T> T getOrSet(ConfigurationNode node, T def, Supplier<T> getter)
    {
        T obj = getter.get();
        if(node.isVirtual() || obj == null)
        {
            node.setValue(def);
            return def;
        }

        return obj;
    }

    @Listener
    public void onGamePreInit(GamePreInitializationEvent event) throws IOException, SAXException
    {
        CommentedConfigurationNode root = configManager.load();
        try
        {
            PermissionLayer.register("sponge", SpongeProviders.PERMISSION);
            EconomyLayer.register("sponge", SpongeProviders.ECONOMY);

            CommentedConfigurationNode dbConfig = root.getNode("database");
            MineCityConfig config = new MineCityConfig();
            config.dbUrl = getOrSetStr(dbConfig.getNode("url"), config.dbUrl);
            config.dbUser = Optional.ofNullable(getOrSetStr(dbConfig.getNode("user"), "")).filter(u-> !u.isEmpty()).orElse(null);
            config.dbPass = Optional.ofNullable(getOrSetStr(dbConfig.getNode("pass"), "")).filter(p-> !p.isEmpty()).map(String::getBytes).orElse(null);
            config.locale = Locale.forLanguageTag(Optional.ofNullable(getOrSetStr(root.getNode("general", "language"), "en")).filter(l->!l.isEmpty()).orElse("en"));
            config.useTitle = getOrSetBool(root.getNode("general", "use-titles"), true);

            CommentedConfigurationNode permsConfig = root.getNode("permissions", "default");
            config.defaultNatureDisableCities = getOrSetBool(permsConfig.getNode("nature", "enable-city-creation"), true);
            config.economy = getOrSetStr(root.getNode("manager", "economy"), "none");
            config.permission = getOrSetStr(root.getNode("manager", "permissions"), "sponge");

            for(PermissionFlag flag: PermissionFlag.values())
            {
                adjustDefaultFlag(permsConfig.getNode("nature", flag.name()), flag, flag.defaultNature, config.defaultNatureFlags);
                adjustDefaultFlag(permsConfig.getNode("city", flag.name()), flag, flag.defaultCity, config.defaultCityFlags);
                adjustDefaultFlag(permsConfig.getNode("plot", flag.name()), flag, flag.defaultPlot, config.defaultPlotFlags);
                adjustDefaultFlag(permsConfig.getNode("reserve", flag.name()), flag, flag.defaultReserve, config.defaultReserveFlags);
            }

            transformer = new SpongeTransformer();
            transformer.parseXML(MineCity.class.getResourceAsStream("/assets/minecity/messages-en.xml"));
            lang = config.locale.toLanguageTag();
            if(!lang.equals("en"))
            {
                try
                {
                    InputStream resource = MineCity.class.getResourceAsStream("/assets/minecity/messages-"+lang +".xml");
                    if(resource != null)
                    {
                        try
                        {
                            transformer.parseXML(resource);
                        }
                        finally
                        {
                            resource.close();
                        }
                    }
                    else
                    {
                        logger.error("There're no translations to "+lang+" available.");
                        lang = "en";
                    }
                }
                catch(Exception e)
                {
                    logger.error("Failed to load the "+lang+" translations", e);
                }
            }

            CommentedConfigurationNode limits = root.getNode("limits");
            config.limits.cities = getOrSetInt(limits.getNode("cities"), -1);
            config.limits.claims = getOrSetInt(limits.getNode("claims"), -1);
            config.limits.islands = getOrSetInt(limits.getNode("islands"), -1);

            CommentedConfigurationNode costs = root.getNode("costs");
            config.costs.cityCreation = getOrSetDouble(costs.getNode("city", "creation"), 1000);
            config.costs.islandCreation = getOrSetDouble(costs.getNode("island", "creation"), 500);
            config.costs.claim = getOrSetDouble(costs.getNode("chunk", "claim"), 25);
            this.config = config;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            Sponge.getServer().shutdown();
            throw e;
        }
        finally
        {
            configManager.save(root);
        }
    }

    private void adjustDefaultFlag(CommentedConfigurationNode node, PermissionFlag flag, boolean def, SimpleFlagHolder holder)
    {
        boolean allow = getOrSetBool(node.getNode("allow"), def);
        String msg = getOrSetStr(node.getNode("message"), "");

        if(!msg.isEmpty())
            holder.getDefaultMessages().put(flag, Message.string(msg));

        if(!allow)
            holder.deny(flag);
    }

    @Listener
    public void onGameServerAboutToStart(GameAboutToStartServerEvent event) throws DataSourceException, SAXException, IOException
    {
        try
        {
            sponge = new MineCitySponge(this, config, transformer, logger);
            ReactiveLayer.setManipulator(new SpongeManipulator(sponge));

            sponge.mineCity.dataSource.initDB();
            sponge.mineCity.commands.parseXml(MineCity.class.getResourceAsStream("/assets/minecity/commands-"+lang+".xml"));

            sponge.mineCity.commands.getRootCommands().stream().forEachOrdered(name->
                    Sponge.getCommandManager().register(this, new SpongeRootCommand(sponge, name), name)
            );

            reloadTask = Sponge.getScheduler().createTaskBuilder()
                    .async()
                    .execute(sponge.mineCity::reloadQueuedChunk)
                    .intervalTicks(1)
                    .delayTicks(1)
                    .submit(this);
        }
        catch(Exception e)
        {
            logger.error("Failed to load MineCity, shutting down the server", e);
            Sponge.getServer().shutdown();
            throw e;
        }
    }

    @Listener
    public void onGameServerStopped(GameStoppedServerEvent event)
    {
        if(sponge != null)
        {
            sponge.loadingTasks.shutdown();
            try
            {
                sponge.loadingTasks.awaitTermination(5, TimeUnit.SECONDS);
            }
            catch(InterruptedException e)
            {
                logger.error("Failed to wait the loading tasks completes", e);
                try
                {
                    sponge.loadingTasks.shutdownNow();
                }
                catch(Exception e2)
                {
                    logger.error("Failed to shutdown the loading tasks", e2);
                }
            }

            try
            {
                sponge.mineCity.dataSource.close();
            }
            catch(DataSourceException e)
            {
                logger.error("Failed to close the dataSource", e);
            }
        }

        if(reloadTask != null)
            reloadTask.cancel();
    }

    @Listener
    public void onInteractBlock(final InteractBlockEvent.Secondary event, @First Player player)
    {
        ItemStack stackInHand = player.getItemInHand(HandTypes.MAIN_HAND).orElseGet(()-> ItemStack.of(ItemTypes.NONE, 0));
        player.sendMessage(Text.builder("HAND_ID: "+stackInHand.getItem().getName() ).color(TextColors.YELLOW).build());
        player.sendMessage(Text.builder("HAND_DURABILITY: "+stackInHand.get(DurabilityData.class).map(DurabilityData::durability).map(BaseValue::get) ).color(TextColors.YELLOW).build());
        player.sendMessage(Text.builder("HAND_STATE: "+stackInHand.get(BlockItemData.class).map(BlockItemData::state).map(BaseValue::get) ).color(TextColors.YELLOW).build());
        player.sendMessage(Text.builder("HAND_NAME_ID: "+stackInHand.getTranslation().getId() ).color(TextColors.YELLOW).build());
        player.sendMessage(Text.builder("HAND_NAME_EN: "+stackInHand.getTranslation().get(Locale.ENGLISH) ).color(TextColors.YELLOW).build());
        player.sendMessage(Text.builder("HAND_NAME_DEF: ").color(TextColors.YELLOW).append(Text.of(stackInHand.getTranslation())).build());
    }

    @Listener(order = Order.POST)
    public void onWorldLoad(LoadWorldEvent event)
    {
        logger.trace("WLA:"+event.getTargetWorld().getName());
        sponge.loadingTasks.submit(()->
        {
            logger.debug("WLB:"+event.getTargetWorld().getName());
            sponge.world(event.getTargetWorld());
        });
    }

    @Listener(order = Order.POST)
    public void onWorldUnload(UnloadWorldEvent event)
    {
        logger.trace("WUA:"+event.getTargetWorld().getName());
        sponge.loadingTasks.submit(()->
        {
            logger.debug("WUB:"+event.getTargetWorld().getName());
            sponge.mineCity.unloadNature(sponge.world(event.getTargetWorld()));
        });
    }

    @Listener(order = Order.PRE)
    public void onChunkLoadPre(LoadChunkEvent event)
    {
        Chunk chunk = event.getTargetChunk();
        logger.info("CLPA:"+ReactiveLayer.getChunk(chunk));
        Vector3i pos = chunk.getPosition();
        logger.info("CLPB:"+(chunk instanceof MixedChunk));
    }

    @Listener(order = Order.POST)
    public void onChunkLoad(LoadChunkEvent event)
    {
        Chunk chunk = event.getTargetChunk();
        logger.trace("CLA:"+chunk);
        sponge.loadingTasks.submit(()->
        {
            logger.debug("CLB:"+chunk);
            try
            {
                sponge.mineCity.loadChunk(sponge.chunk(chunk));
            }
            catch(Exception e)
            {
                Vector3i position = chunk.getPosition();
                logger.error(
                        "Failed to load the chunk "+
                                chunk.getWorld().getName()+" "+position.getX()+" "+position.getY()+" "+position.getZ(),
                        e
                );
            }
        });
    }

    @Listener(order = Order.POST)
    public void onChunkUnload(UnloadChunkEvent event)
    {
        logger.trace("CUA:"+event.getTargetChunk()+"");
        sponge.loadingTasks.submit(()-> {
            logger.debug("CUB:"+event.getTargetChunk()+"");
            sponge.mineCity.unloadChunk(sponge.chunk(event.getTargetChunk()));
        });
    }

    @Listener(order = Order.PRE)
    public void onEntitySpawn(SpawnEntityEvent event)
    {
        event.getEntities().forEach(ReactiveLayer::getEntityData);
    }
}
