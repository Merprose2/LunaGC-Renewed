package emu.grasscutter.game.player;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import dev.morphia.annotations.Entity;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.net.proto.AvatarTeamOuterClass.AvatarTeam;
import java.util.*;

@Entity
public final class TeamInfo {
    private String name;
    private List<Integer> avatars;

    public TeamInfo() {
        this.name = "";
        this.avatars = new ArrayList<>(GAME_OPTIONS.avatarLimits.singlePlayerTeam);
    }

    public TeamInfo(List<Integer> avatars) {
        this.name = "";
        this.avatars = avatars;
		this.ensureValidFields();
    }

    public String getName() {
		this.ensureValidFields();
        return name;
    }

    public void setName(String name) {
		this.name = name == null ? "" : name;
    }

    public List<Integer> getAvatars() {
		this.ensureValidFields();
        return avatars;
    }

    public int size() {
        return getAvatars().size();
    }

    public boolean contains(Avatar avatar) {
        return avatar != null && getAvatars().contains(avatar.getAvatarId());
    }

    public boolean addAvatar(Avatar avatar) {
        if (avatar == null || contains(avatar)) {
            return false;
        }

        getAvatars().add(avatar.getAvatarId());

        return true;
    }

    public boolean removeAvatar(int slot) {
        if (size() <= 1) {
            return false;
        }

        getAvatars().remove(slot);

        return true;
    }

    public void copyFrom(TeamInfo team) {
        copyFrom(team, GAME_OPTIONS.avatarLimits.singlePlayerTeam);
    }

    public void copyFrom(TeamInfo team, int maxTeamSize) {
		this.ensureValidFields();

        if (team == null) {
            this.getAvatars().clear();
            return;
        }

        team.ensureValidFields();
        // Clone avatar ids from team to copy from
        List<Integer> avatarIds = new ArrayList<>(team.getAvatars());

        // Clear current avatar list first
        this.getAvatars().clear();

        // Copy from team
        int len = Math.min(avatarIds.size(), maxTeamSize);
        for (int i = 0; i < len; i++) {
            Integer id = avatarIds.get(i);
            if (id != null) {
                this.getAvatars().add(id);
            }
        }
    }

    public void ensureValidFields() {
        if (this.name == null) {
            this.name = "";
        }

        if (this.avatars == null) {
            this.avatars = new ArrayList<>(GAME_OPTIONS.avatarLimits.singlePlayerTeam);
        }
    }

    public boolean sanitize(Player player, int maxTeamSize) {
        boolean changed = false;

        if (this.name == null) {
            this.name = "";
            changed = true;
        }

        if (this.avatars == null) {
            this.avatars = new ArrayList<>(GAME_OPTIONS.avatarLimits.singlePlayerTeam);
            return true;
        }

        LinkedHashSet<Integer> cleanedAvatarIds = new LinkedHashSet<>();

        for (Integer avatarId : new ArrayList<>(this.avatars)) {
            if (avatarId == null) {
                changed = true;
                continue;
            }

            if (cleanedAvatarIds.size() >= maxTeamSize) {
                changed = true;
                continue;
            }

            if (player == null || player.getAvatars().getAvatarById(avatarId) == null) {
                changed = true;
                continue;
            }

            if (!cleanedAvatarIds.add(avatarId)) {
                changed = true;
            }
        }

        List<Integer> cleanedList = new ArrayList<>(cleanedAvatarIds);
        if (!this.avatars.equals(cleanedList)) {
            this.avatars.clear();
            this.avatars.addAll(cleanedList);
            changed = true;
        }

        return changed;
    }

    public AvatarTeam toProto(Player player) {
		this.ensureValidFields();
		
        AvatarTeam.Builder avatarTeam = AvatarTeam.newBuilder().setTeamName(this.getName());

        for (int i = 0; i < this.getAvatars().size(); i++) {
            Avatar avatar = player.getAvatars().getAvatarById(this.getAvatars().get(i));
			
            if (avatar == null) {
                continue;
            }

            avatarTeam.addAvatarGuidList(avatar.getGuid());
        }

        return avatarTeam.build();
    }
}
