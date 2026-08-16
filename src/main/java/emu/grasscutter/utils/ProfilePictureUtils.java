package emu.grasscutter.utils;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarCostumeData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ProfilePictureOuterClass.ProfilePicture;
import java.nio.file.Files;
import java.util.*;

public final class ProfilePictureUtils {
    private static final String UNLOCK_BY_AVATAR = "PROFILE_PICTURE_UNLOCK_BY_AVATAR";
    private static final String UNLOCK_BY_COSTUME = "PROFILE_PICTURE_UNLOCK_BY_COSTUME";

    private static boolean loaded = false;

    private static final Map<Integer, Integer> avatarToProfilePictureId = new HashMap<>();
    private static final Map<Integer, Integer> profilePictureIdToAvatar = new HashMap<>();
    private static final Map<Integer, Integer> costumeToProfilePictureId = new HashMap<>();
    private static final Map<Integer, Integer> profilePictureIdToCostume = new HashMap<>();

    private ProfilePictureUtils() {}

    public static ProfilePicture buildProfilePicture(Player player) {
        int stored = player.getHeadImage();
        int profilePictureId = resolveProfilePictureId(player, stored);
        int avatarId = resolveAvatarId(player, stored);

        if (avatarId == 0 && profilePictureId != 0) {
            avatarId = profilePictureIdToAvatar(profilePictureId);
        }

        if (profilePictureId == 0 && avatarId != 0) {
            profilePictureId = avatarToProfilePictureId(avatarId);
        }

        var builder = ProfilePicture.newBuilder();

        if (avatarId != 0) {
            builder.setAvatarId(avatarId);
            builder.setCostumeId(player.getCostumeFrom(avatarId));
        }

        if (profilePictureId != 0) {
            builder.setProfilePictureId(profilePictureId);
        }

        return builder.build();
    }

    public static int resolveProfilePictureId(Player player, int requestedId) {
        load();

        if (requestedId <= 0) {
            return fallbackProfilePictureId(player);
        }

        if (profilePictureIdToAvatar.containsKey(requestedId)
                || profilePictureIdToCostume.containsKey(requestedId)) {
            return requestedId;
        }

        int byAvatar = avatarToProfilePictureId(requestedId);
        if (byAvatar != 0) {
            return byAvatar;
        }

        int byCostume = costumeToProfilePictureId.getOrDefault(requestedId, 0);
        if (byCostume != 0) {
            return byCostume;
        }

        return fallbackProfilePictureId(player);
    }

    public static int resolveAvatarId(Player player, int requestedId) {
        load();

        if (requestedId <= 0) {
            return fallbackAvatarId(player);
        }

        if (avatarToProfilePictureId.containsKey(requestedId)) {
            return requestedId;
        }

        int avatarId = profilePictureIdToAvatar(requestedId);
        if (avatarId != 0) {
            return avatarId;
        }

        int costumeId = profilePictureIdToCostume.getOrDefault(requestedId, 0);
        if (costumeId != 0) {
            AvatarCostumeData costumeData = GameData.getAvatarCostumeDataMap().get(costumeId);
            if (costumeData != null) {
                return costumeData.getCharacterId();
            }
        }

        return fallbackAvatarId(player);
    }

    public static List<Integer> getUnlockedProfilePictureIds(Player player) {
        load();

        Set<Integer> result = new LinkedHashSet<>();

        try {
            player.getAvatars().loadFromDatabase();
        } catch (Exception ignored) {
        }

        for (Avatar avatar : player.getAvatars()) {
            int profilePictureId = avatarToProfilePictureId(avatar.getAvatarId());
            if (profilePictureId != 0) {
                result.add(profilePictureId);
            }

            int costumeProfilePictureId = costumeToProfilePictureId.getOrDefault(avatar.getCostume(), 0);
            if (costumeProfilePictureId != 0) {
                result.add(costumeProfilePictureId);
            }
        }

        if (player.getCostumeList() != null) {
            for (int costumeId : player.getCostumeList()) {
                int profilePictureId = costumeToProfilePictureId.getOrDefault(costumeId, 0);
                if (profilePictureId != 0) {
                    result.add(profilePictureId);
                }
            }
        }

        int current = resolveProfilePictureId(player, player.getHeadImage());
        if (current != 0) {
            result.add(current);
        }

        return new ArrayList<>(result);
    }

    private static int fallbackProfilePictureId(Player player) {
        int avatarId = fallbackAvatarId(player);
        return avatarId == 0 ? 0 : avatarToProfilePictureId(avatarId);
    }

    private static int fallbackAvatarId(Player player) {
        if (player.getMainCharacterId() != 0) {
            return player.getMainCharacterId();
        }

        try {
            player.getAvatars().loadFromDatabase();
        } catch (Exception ignored) {
        }

        for (Avatar avatar : player.getAvatars()) {
            return avatar.getAvatarId();
        }

        return 10000007; // Lumine fallback
    }

    private static int avatarToProfilePictureId(int avatarId) {
        load();
        return avatarToProfilePictureId.getOrDefault(avatarId, 0);
    }

    private static int profilePictureIdToAvatar(int profilePictureId) {
        load();
        return profilePictureIdToAvatar.getOrDefault(profilePictureId, 0);
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }

        loaded = true;

        try {
            var path = FileUtils.getExcelPath("ProfilePictureExcelConfigData.json");

            if (!Files.exists(path)) {
                Grasscutter.getLogger().warn("ProfilePictureExcelConfigData.json was not found.");
                return;
            }

            List<ProfilePictureExcelEntry> entries = JsonUtils.loadToList(path, ProfilePictureExcelEntry.class);

            for (ProfilePictureExcelEntry entry : entries) {
                if (entry.id <= 0 || entry.unlockParam <= 0 || entry.unlockType == null) {
                    continue;
                }

                switch (entry.unlockType) {
                    case UNLOCK_BY_AVATAR -> {
                        avatarToProfilePictureId.put(entry.unlockParam, entry.id);
                        profilePictureIdToAvatar.put(entry.id, entry.unlockParam);
                    }
                    case UNLOCK_BY_COSTUME -> {
                        costumeToProfilePictureId.put(entry.unlockParam, entry.id);
                        profilePictureIdToCostume.put(entry.id, entry.unlockParam);
                    }
                    default -> {
                    }
                }
            }

            Grasscutter.getLogger()
                    .info(
                            "Loaded {} avatar profile pictures and {} costume profile pictures.",
                            avatarToProfilePictureId.size(),
                            costumeToProfilePictureId.size());
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Failed to load profile picture data.", e);
        }
    }

    private static final class ProfilePictureExcelEntry {
        private int id;

        @SerializedName("AJFEJFNMCKP")
        private String unlockType;

        private int unlockParam;
    }
}