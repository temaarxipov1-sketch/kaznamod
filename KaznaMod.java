package ru.kazna;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

public class KaznaMod implements ModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static GovState state = new GovState();
    private static Path file;
    private static final Random RNG = new Random();

    public static final String ARTEM = "Президент Артём III";
    private static final int COUP_PRICE = 32;
    private static final int DECREE_PRICE = 1;

    private static final List<String> FUNNY_LAWS = List.of(
        "Каждый житель обязан хотя бы раз в день поздороваться с курицей.",
        "Запрещено строить дома без хотя бы одного бесполезного балкона.",
        "Крипер, взорвавший чужой дом, обязан лично извиниться.",
        "По средам все ходят задом наперёд (по возможности).",
        "Любой, кто нашёл алмазы, обязан показать их президенту. Просто посмотреть.",
        "Лава в радиусе 10 блоков от дома - это не дизайн, а угроза.",
        "Каждый гражданин обязан иметь лопату на чёрный день.",
        "Зомби - тоже граждане, но без права голоса.",
        "Тот, кто украл у друга яблоко, назначается придворным яблочником.",
        "Шутки про Артёма III разрешены, если они смешнее самого Артёма III.",
        "Запрещено копать вниз. Артём когда-то упал в лаву и до сих пор обижен."
    );
    private static final List<String> SPEECHES = List.of(
        "Граждане! Я вас люблю. Но налоги всё равно будут.",
        "Моя мудрость безгранична, как и мой счёт за золотую ванну.",
        "Кто-то сказал, что я ничего не делаю? Я делаю! Я только что моргнул.",
        "Заявляю официально: вчера я победил дракона. Во сне, но очень убедительно.",
        "Слышал, что свиньи недовольны. Свиньи, подайте жалобу в письменном виде.",
        "Мой любимый блок - золотой. Второй любимый - тоже золотой.",
        "Сегодня отличный день, чтобы ничего не менять. Продолжаем!"
    );
    private static final List<String> GIFT_REASONS = List.of(
        "на день рождения моего хомяка", "потому что у него хорошая причёска",
        "за то, что он вчера не взорвал мой дом", "просто так, я сегодня добрый",
        "за красивую табличку у дома"
    );
    private static final List<String> FINE_REASONS = List.of(
        "за слишком серьёзное лицо", "за подозрительную улыбку",
        "за то, что прошёл мимо моей статуи и не поклонился",
        "за подозрительно хорошее настроение", "за ношение шлема в мирное время"
    );
    private static final List<String> STATUE_REASONS = List.of(
        "на золотую статую самого себя в полный рост", "на золотой унитаз для дворца",
        "на корону, ну очень золотую", "на банкет для хомяка и, конечно, для меня"
    );
    private static final List<String> EXCUSES = List.of(
        "у меня сегодня выходной", "там больше трёх слов, это слишком сложно",
        "мой хомяк против", "я перепутал её с меню",
        "не понял, но на всякий случай нет"
    );

    private static String pick(List<String> l) { return l.get(RNG.nextInt(l.size())); }

    private static String rulerName() {
        return state.rulerUuid == null ? ARTEM : state.rulerName;
    }

    private static String artemSays(String msg) {
        return "§d[" + ARTEM + "] §f" + msg;
    }

    @Override
    public void onInitialize() {
        file = FabricLoader.getInstance().getConfigDir().resolve("kazna.json");

        ServerLifecycleEvents.SERVER_STARTED.register(server -> load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long period = Math.max(1, state.intervalMinutes) * 1200L;
            int tick = server.getTickCount();
            if (tick > 0 && tick % period == 0) collectTaxes(server);

            long decreePeriod = Math.max(1, state.artemDecreeMinutes) * 1200L;
            if (tick > 0 && tick % decreePeriod == 0 && state.rulerUuid == null) artemDecree(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            handler.player.sendSystemMessage(Component.literal(artemSays(
                state.rulerUuid == null
                    ? "Приветствую, гражданин! Я ваш президент. Да-да, тот самый. Налоги платить не забываем!"
                    : "Приветствую! Артём III в изгнании, страной правит " + state.rulerName + ". Пока что."))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("gov")
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("coup").executes(ctx -> coup(ctx)))
                .then(Commands.literal("claim").executes(ctx -> coup(ctx)))
                .then(Commands.literal("decree").executes(ctx -> decreeCmd(ctx)))
                .then(Commands.literal("petition")
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> petition(ctx))))
                .then(Commands.literal("abdicate").executes(ctx -> abdicate(ctx)))
                .then(Commands.literal("ruler")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> transfer(ctx))))
                .then(Commands.literal("law")
                    .then(Commands.literal("list").executes(ctx -> lawList(ctx.getSource())))
                    .then(Commands.literal("add")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                            .executes(ctx -> lawAdd(ctx))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                            .executes(ctx -> lawRemove(ctx)))))
                .then(Commands.literal("tax")
                    .then(Commands.argument("percent", IntegerArgumentType.integer(0, 50))
                        .executes(ctx -> setTax(ctx))))
                .then(Commands.literal("interval")
                    .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
                        .executes(ctx -> setInterval(ctx))))
                .then(Commands.literal("treasury").executes(ctx -> treasury(ctx.getSource())))
                .then(Commands.literal("deposit")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> deposit(ctx))))
                .then(Commands.literal("withdraw")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> withdraw(ctx))))
                .then(Commands.literal("pay")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> pay(ctx)))))
                .then(Commands.literal("fine")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> fine(ctx)))))
            );
        });
    }

    // ---------- сохранение ----------

    private static void load() {
        try {
            if (Files.exists(file)) {
                GovState s = GSON.fromJson(Files.readString(file), GovState.class);
                if (s != null) state = s;
            }
        } catch (Exception e) {
            System.err.println("[Казна] Не удалось загрузить kazna.json: " + e);
        }
    }

    private static void save() {
        try {
            Files.writeString(file, GSON.toJson(state));
        } catch (Exception e) {
            System.err.println("[Казна] Не удалось сохранить kazna.json: " + e);
        }
    }

    // ---------- вспомогательное ----------

    private static void say(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
    }

    private static int fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(msg));
        return 0;
    }

    private static void broadcast(MinecraftServer server, String msg) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }

    private static boolean isRuler(ServerPlayer p) {
        return state.rulerUuid != null && state.rulerUuid.equals(p.getUUID().toString());
    }

    /** Возвращает игрока-правителя или null (и пишет ошибку). */
    private static ServerPlayer requireRuler(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!isRuler(p)) {
            fail(ctx.getSource(), state.rulerUuid == null
                ? ARTEM + " не разрешает тебе это! Попробуй /gov petition <текст> или свергни его: /gov coup"
                : "Это может делать только правитель. Сейчас правит: " + state.rulerName);
            return null;
        }
        return p;
    }

    // ---------- команды ----------

    private static int help(CommandSourceStack src) {
        say(src, "§6=== Казна: команды ===");
        say(src, "§e/gov info §7- состояние страны");
        say(src, "§e/gov coup §7- свергнуть Артёма III (взятка гвардии: " + COUP_PRICE + " слитков)");
        say(src, "§e/gov petition <текст> §7- подать петицию Артёму III (как повезёт)");
        say(src, "§e/gov decree §7- вызвать указ Артёма III (" + DECREE_PRICE + " слиток секретарю)");
        say(src, "§e/gov abdicate §7- отречься от власти");
        say(src, "§e/gov ruler <игрок> §7- передать власть");
        say(src, "§e/gov law list|add|remove §7- законы");
        say(src, "§e/gov tax <0-50> §7- налог в % с золотых слитков");
        say(src, "§e/gov interval <минуты> §7- как часто собирать налог");
        say(src, "§e/gov treasury §7- казна");
        say(src, "§e/gov deposit <n> §7- пожертвовать слитки в казну");
        say(src, "§e/gov withdraw <n> §7- взять из казны (правитель)");
        say(src, "§e/gov pay <игрок> <n> §7- выплата из казны (правитель)");
        say(src, "§e/gov fine <игрок> <n> §7- штраф в казну (правитель)");
        return 1;
    }

    private static int info(CommandSourceStack src) {
        say(src, "§6=== Государство ===");
        say(src, "§eПравитель: §f" + rulerName());
        say(src, "§eКазна: §f" + state.treasury + " золотых слитков");
        say(src, "§eНалог: §f" + state.taxPercent + "% каждые " + state.intervalMinutes + " мин.");
        say(src, "§eЗаконов: §f" + state.laws.size() + " (/gov law list)");
        return 1;
    }

    private static int coup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (state.rulerUuid != null)
            return fail(ctx.getSource(), "Трон уже занят игроком: " + state.rulerName);
        if (Gold.count(p) < COUP_PRICE)
            return fail(ctx.getSource(), "Для переворота нужно " + COUP_PRICE
                + " золотых слитков на взятку гвардии. " + ARTEM + " дёшево не продаётся!");
        Gold.take(p, COUP_PRICE);
        state.treasury += COUP_PRICE;
        state.rulerUuid = p.getUUID().toString();
        state.rulerName = p.getName().getString();
        save();
        MinecraftServer server = ctx.getSource().getServer();
        broadcast(server, artemSays("Меня свергли!! Охрана, где охрана?! А, вы уже взяли взятку..."));
        broadcast(server, "§6" + state.rulerName + " совершил переворот и теперь правитель!");
        return 1;
    }

    private static int petition(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (state.rulerUuid != null)
            return fail(ctx.getSource(), "Сейчас правит игрок " + state.rulerName + ", просто попроси его.");
        String text = StringArgumentType.getString(ctx, "text");
        String who = p.getName().getString();
        MinecraftServer server = ctx.getSource().getServer();
        if (RNG.nextBoolean()) {
            state.laws.add(text);
            save();
            broadcast(server, artemSays("Петиция от " + who + "? Подписал не читая! Закон №"
                + state.laws.size() + ": " + text));
        } else {
            broadcast(server, artemSays("Петицию от " + who + " отклоняю, потому что " + pick(EXCUSES) + "."));
        }
        return 1;
    }

    private static int decreeCmd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (state.rulerUuid != null)
            return fail(ctx.getSource(), ARTEM + " в изгнании, указы издавать некому.");
        if (Gold.count(p) < DECREE_PRICE)
            return fail(ctx.getSource(), "Секретарь Артёма берёт " + DECREE_PRICE + " слиток за запись на приём.");
        Gold.take(p, DECREE_PRICE);
        state.treasury += DECREE_PRICE;
        artemDecree(ctx.getSource().getServer());
        return 1;
    }

    private static int abdicate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = requireRuler(ctx);
        if (p == null) return 0;
        broadcast(ctx.getSource().getServer(), "§6" + state.rulerName + " отрекся от власти. " + ARTEM + " вернулся из изгнания и снова у руля!");
        state.rulerUuid = null;
        state.rulerName = null;
        save();
        return 1;
    }

    private static int transfer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (requireRuler(ctx) == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        state.rulerUuid = target.getUUID().toString();
        state.rulerName = target.getName().getString();
        save();
        broadcast(ctx.getSource().getServer(), "§6Новый правитель: " + state.rulerName);
        return 1;
    }

    private static int lawList(CommandSourceStack src) {
        if (state.laws.isEmpty()) {
            say(src, "§7Законов пока нет.");
            return 1;
        }
        say(src, "§6=== Законы ===");
        for (int i = 0; i < state.laws.size(); i++) {
            say(src, "§e" + (i + 1) + ". §f" + state.laws.get(i));
        }
        return 1;
    }

    private static int lawAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (requireRuler(ctx) == null) return 0;
        String text = StringArgumentType.getString(ctx, "text");
        state.laws.add(text);
        save();
        broadcast(ctx.getSource().getServer(), "§6Новый закон №" + state.laws.size() + ": §f" + text);
        return 1;
    }

    private static int lawRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (requireRuler(ctx) == null) return 0;
        int n = IntegerArgumentType.getInteger(ctx, "number");
        if (n > state.laws.size()) return fail(ctx.getSource(), "Нет закона с таким номером.");
        String removed = state.laws.remove(n - 1);
        save();
        broadcast(ctx.getSource().getServer(), "§6Закон отменен: §f" + removed);
        return 1;
    }

    private static int setTax(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (requireRuler(ctx) == null) return 0;
        state.taxPercent = IntegerArgumentType.getInteger(ctx, "percent");
        save();
        broadcast(ctx.getSource().getServer(), "§6Налог изменен: " + state.taxPercent + "%");
        return 1;
    }

    private static int setInterval(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (requireRuler(ctx) == null) return 0;
        state.intervalMinutes = IntegerArgumentType.getInteger(ctx, "minutes");
        save();
        broadcast(ctx.getSource().getServer(), "§6Налог теперь собирается раз в " + state.intervalMinutes + " мин.");
        return 1;
    }

    private static int treasury(CommandSourceStack src) {
        say(src, "§eВ казне: §f" + state.treasury + " золотых слитков");
        return 1;
    }

    private static int deposit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (Gold.count(p) < amount) return fail(ctx.getSource(), "У тебя нет столько слитков.");
        Gold.take(p, amount);
        state.treasury += amount;
        save();
        say(ctx.getSource(), "§aВ казну внесено: " + amount + ". Теперь в казне " + state.treasury);
        return 1;
    }

    private static int withdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = requireRuler(ctx);
        if (p == null) return 0;
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (state.treasury < amount) return fail(ctx.getSource(), "В казне только " + state.treasury);
        state.treasury -= amount;
        Gold.give(p, amount);
        save();
        broadcast(ctx.getSource().getServer(), "§6Правитель взял из казны " + amount + " слитков. Осталось: " + state.treasury);
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (requireRuler(ctx) == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (state.treasury < amount) return fail(ctx.getSource(), "В казне только " + state.treasury);
        state.treasury -= amount;
        Gold.give(target, amount);
        save();
        broadcast(ctx.getSource().getServer(), "§6Выплата из казны: " + target.getName().getString()
            + " получает " + amount + " слитков.");
        return 1;
    }

    private static int fine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (requireRuler(ctx) == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        int taken = Gold.take(target, amount);
        state.treasury += taken;
        save();
        broadcast(ctx.getSource().getServer(), "§cШтраф: " + target.getName().getString()
            + " платит в казну " + taken + " из " + amount + " слитков.");
        return 1;
    }

    // ---------- налоги ----------

    private static void collectTaxes(MinecraftServer server) {
        if (state.taxPercent <= 0) return;
        int total = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            int gold = Gold.count(p);
            int tax = gold * state.taxPercent / 100;
            if (tax > 0) {
                int taken = Gold.take(p, tax);
                total += taken;
                p.sendSystemMessage(Component.literal("§eС тебя собрали налог: " + taken + " золотых слитков."));
            }
        }
        state.treasury += total;
        save();
        broadcast(server, "§6Сбор налогов завершен (" + state.taxPercent + "%). В казну поступило "
            + total + ". Всего в казне: " + state.treasury);
    }

    // ---------- безумные указы Артёма III ----------

    private static void artemDecree(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        ServerPlayer target = players.get(RNG.nextInt(players.size()));
        String name = target.getName().getString();

        switch (RNG.nextInt(6)) {
            case 0 -> {
                String law = pick(FUNNY_LAWS);
                if (state.laws.contains(law)) {
                    broadcast(server, artemSays(pick(SPEECHES)));
                } else {
                    state.laws.add(law);
                    broadcast(server, artemSays("Подписал указ, не читая! Закон №" + state.laws.size() + ": " + law));
                }
            }
            case 1 -> {
                int t = RNG.nextInt(31);
                state.taxPercent = t;
                broadcast(server, artemSays("Бросил кубик, и вот результат: налог теперь " + t + "%! Судьба решила."));
            }
            case 2 -> {
                int g = Math.min(state.treasury, 1 + RNG.nextInt(5));
                if (g <= 0) {
                    broadcast(server, artemSays("Хотел подарить " + name + " золота, но казна пуста. Всё из-за налогов, то есть из-за их отсутствия."));
                } else {
                    state.treasury -= g;
                    Gold.give(target, g);
                    broadcast(server, artemSays("Дарю " + g + " слитков игроку " + name + " " + pick(GIFT_REASONS) + "!"));
                }
            }
            case 3 -> {
                int f = 1 + RNG.nextInt(3);
                int taken = Gold.take(target, f);
                if (taken == 0) {
                    broadcast(server, artemSays("Хотел оштрафовать " + name + " " + pick(FINE_REASONS)
                        + ", но у него нет золота. Бедность - тоже преступление!"));
                } else {
                    state.treasury += taken;
                    broadcast(server, artemSays("Штраф игроку " + name + " на " + taken + " слитков " + pick(FINE_REASONS) + "."));
                }
            }
            case 4 -> {
                int g = Math.min(state.treasury, 1 + RNG.nextInt(5));
                if (g <= 0) {
                    broadcast(server, artemSays("Казна пуста, статую себе строить не на что. Печаль."));
                } else {
                    state.treasury -= g;
                    broadcast(server, artemSays("Потратил " + g + " слитков из казны " + pick(STATUE_REASONS) + ". Вы мне ещё спасибо скажете!"));
                }
            }
            default -> broadcast(server, artemSays(pick(SPEECHES)));
        }
        save();
    }
}
