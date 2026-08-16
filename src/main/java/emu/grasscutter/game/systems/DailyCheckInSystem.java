package emu.grasscutter.game.systems;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.mail.Mail.MailContent;
import emu.grasscutter.game.mail.Mail.MailItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.Utils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles LunaGC's rolling daily check-in reward system.
 *
 * <p>The system is based on Grasscutter PR #2550, with several changes:
 *
 * <ul>
 *     <li>Check-in progress does not reset when a day is missed.</li>
 *     <li>The cycle advances once per unique calendar day that the player logs in.</li>
 *     <li>After day 31, the next successful check-in starts again at day 1.</li>
 *     <li>State is stored directly on Player rather than using raw player-property IDs.</li>
 *     <li>Rewards are increased to suit LunaGC's private-server/sandbox progression.</li>
 * </ul>
 */
public final class DailyCheckInSystem {

    private static final int CYCLE_DAYS = 31;
    private static final int MAIL_EXPIRATION_DAYS = 7;

    private static final String MAIL_TITLE = "Daily Check-In Reward";
    private static final String MAIL_SENDER = "Daily Check-In";

    /*
     * Reward cycle totals:
     *
     * Primogems:                1,600
     * Hero's Wit:                  72
     * Mystic Enhancement Ore:      60
     * Mora:                    600,000
     * Food items:                  80
     *
     * 1,600 Primogems = exactly 10 wishes.
     */
    private static final CheckInReward[] CHECK_IN_REWARDS = {
            // Day 1
            new CheckInReward(
                    104003,
                    3,
                    "Hero's Wit"),

            // Day 2
            new CheckInReward(
                    104013,
                    10,
                    "Mystic Enhancement Ore"),

            // Day 3
            new CheckInReward(
                    202,
                    50_000,
                    "Mora"),

            // Day 4
            new CheckInReward(
                    201,
                    320,
                    "Primogem"),

            // Day 5
            new CheckInReward(
                    108032,
                    20,
                    "Sweet Madame"),

            // Day 6
            new CheckInReward(
                    104003,
                    3,
                    "Hero's Wit"),

            // Day 7
            new CheckInReward(
                    202,
                    75_000,
                    "Mora"),

            // Day 8
            new CheckInReward(
                    104003,
                    5,
                    "Hero's Wit"),

            // Day 9
            new CheckInReward(
                    104013,
                    10,
                    "Mystic Enhancement Ore"),

            // Day 10
            new CheckInReward(
                    202,
                    50_000,
                    "Mora"),

            // Day 11
            new CheckInReward(
                    201,
                    320,
                    "Primogem"),

            // Day 12
            new CheckInReward(
                    108026,
                    20,
                    "Fried Radish Balls"),

            // Day 13
            new CheckInReward(
                    104003,
                    5,
                    "Hero's Wit"),

            // Day 14
            new CheckInReward(
                    202,
                    75_000,
                    "Mora"),

            // Day 15
            new CheckInReward(
                    104003,
                    8,
                    "Hero's Wit"),

            // Day 16
            new CheckInReward(
                    104013,
                    20,
                    "Mystic Enhancement Ore"),

            // Day 17
            new CheckInReward(
                    202,
                    50_000,
                    "Mora"),

            // Day 18
            new CheckInReward(
                    201,
                    320,
                    "Primogem"),

            // Day 19
            new CheckInReward(
                    108002,
                    20,
                    "Fisherman's Toast"),

            // Day 20
            new CheckInReward(
                    104003,
                    5,
                    "Hero's Wit"),

            // Day 21
            new CheckInReward(
                    202,
                    75_000,
                    "Mora"),

            // Day 22
            new CheckInReward(
                    104003,
                    8,
                    "Hero's Wit"),

            // Day 23
            new CheckInReward(
                    104013,
                    20,
                    "Mystic Enhancement Ore"),

            // Day 24
            new CheckInReward(
                    202,
                    50_000,
                    "Mora"),

            // Day 25
            new CheckInReward(
                    104003,
                    10,
                    "Hero's Wit"),

            // Day 26
            new CheckInReward(
                    108081,
                    20,
                    "Almond Tofu"),

            // Day 27
            new CheckInReward(
                    104003,
                    10,
                    "Hero's Wit"),

            // Day 28
            new CheckInReward(
                    104003,
                    15,
                    "Hero's Wit"),

            // Day 29
            new CheckInReward(
                    202,
                    75_000,
                    "Mora"),

            // Day 30
            new CheckInReward(
                    202,
                    100_000,
                    "Mora"),

            // Day 31 - cycle completion reward
            new CheckInReward(
                    201,
                    640,
                    "Primogem")
    };

    private DailyCheckInSystem() {}

    /**
     * Checks the player's daily reward using the server machine's local date.
     */
    public static void checkAndSend(Player player) {
        checkAndSend(
                player,
                LocalDate.now(ZoneId.systemDefault()));
    }

    /**
     * Processes one daily check-in.
     *
     * <p>A player can receive at most one reward per server-local calendar day.
     * Missing one or more days does not reset progress; the next login simply
     * continues with the next reward in the 31-day cycle.
     *
     * @param player Player logging in.
     * @param today Current server-local date.
     */
    public static void checkAndSend(
            Player player,
            LocalDate today) {

        if (player == null || today == null) {
            return;
        }

        /*
         * Synchronize on the player so two calls occurring during the same
         * session cannot both pass the date check and create duplicate mails.
         */
        synchronized (player) {
            int todayValue =
                    toDateValue(today);

            int lastCheckInDate =
                    player.getLastDailyCheckInDate();

            /*
             * Equal date:
             *   Player already claimed today's reward.
             *
             * Greater date:
             *   The stored date is somehow in the future, most likely due to
             *   the server clock being moved backwards. Do not issue another
             *   reward until the calendar catches up.
             */
            if (lastCheckInDate >= todayValue) {
                return;
            }

            int currentDay =
                    player.getDailyCheckInDay();

            int nextDay;

            /*
             * Existing players have dailyCheckInDay == 0 because this field
             * did not exist previously.
             *
             * Invalid/corrupt values are also safely treated as a new cycle.
             */
            if (currentDay < 1
                    || currentDay >= CYCLE_DAYS) {
                nextDay = 1;
            } else {
                nextDay = currentDay + 1;
            }

            CheckInReward reward =
                    CHECK_IN_REWARDS[nextDay - 1];

            sendDailyReward(
                    player,
                    nextDay,
                    reward);

            /*
             * Persist progression only after the mail has been created and
             * handed to LunaGC's mail system.
             */
            player.setLastDailyCheckInDate(
                    todayValue);

            player.setDailyCheckInDay(
                    nextDay);

            player.save();

            Grasscutter.getLogger()
                    .info(
                            "Player {} (UID: {}) claimed daily check-in day {}: {} x{}",
                            player.getNickname(),
                            player.getUid(),
                            nextDay,
                            reward.name,
                            reward.count);
        }
    }

    /**
     * Converts a date into a stable YYYYMMDD integer.
     *
     * Example:
     * 2026-08-07 -> 20260807
     */
    private static int toDateValue(
            LocalDate date) {

        return date.getYear() * 10_000
                + date.getMonthValue() * 100
                + date.getDayOfMonth();
    }

    /**
     * Creates and sends the reward mail for one day of the cycle.
     */
    private static void sendDailyReward(
            Player player,
            int day,
            CheckInReward reward) {

        StringBuilder content =
                new StringBuilder();

        content.append(
                "Thank you for logging in today, Traveler!");

        content.append(
                "\n\nDaily Check-In: Day ")
                .append(day)
                .append("/")
                .append(CYCLE_DAYS);

        content.append(
                "\nReward: ")
                .append(reward.name)
                .append(" x")
                .append(reward.count);

        /*
         * Make the final day feel like an actual completion milestone.
         */
        if (day == CYCLE_DAYS) {
            content.append(
                    "\n\nYou completed the 31-day check-in cycle!"
                            + " Your next daily check-in will begin a new cycle.");
        }

        MailContent mailContent =
                new MailContent(
                        MAIL_TITLE,
                        content.toString(),
                        MAIL_SENDER);

        List<MailItem> mailItems =
                new ArrayList<>();

        mailItems.add(
                new MailItem(
                        reward.itemId,
                        reward.count));

        long expireTime =
                Utils.getCurrentSeconds()
                        + (MAIL_EXPIRATION_DAYS * 86_400L);

        Mail checkInMail =
                new Mail(
                        mailContent,
                        mailItems,
                        expireTime);

        player.getMailHandler()
                .sendMail(checkInMail);
    }

    /**
     * One entry in the 31-day reward table.
     */
    private static final class CheckInReward {
        private final int itemId;
        private final int count;
        private final String name;

        private CheckInReward(
                int itemId,
                int count,
                String name) {

            this.itemId = itemId;
            this.count = count;
            this.name = name;
        }
    }
}