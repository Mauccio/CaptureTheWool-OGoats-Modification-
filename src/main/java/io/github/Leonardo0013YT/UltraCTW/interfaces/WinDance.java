package io.github.Leonardo0013YT.UltraCTW.interfaces;

import io.github.Leonardo0013YT.UltraCTW.UltraCTW;
import org.bukkit.entity.Player;

public interface WinDance {

    void loadCustoms(UltraCTW plugin, String path);

    void start(Player p, Game game);

    void stop();

}
