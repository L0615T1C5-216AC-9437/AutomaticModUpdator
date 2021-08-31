package automaticModUpdater;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.util.Http;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.Base64Coder;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.ModsDialog;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;

import static mindustry.Vars.ghApi;

public class amu extends Mod {
    public amu() {
        Events.on(ClientLoadEvent.class, event -> {
            if (Core.settings.getLong("amuLastCheck", 0) < System.currentTimeMillis() - 3600000) {
                Core.settings.put("amuLastCheck", System.currentTimeMillis());
                Log.info("Checking for Mod Updates");
                //check if player has chosen to ignore any updates
                HashMap<String, Long> ignoredUpdates = new HashMap<>();
                String ignored = Core.settings.getString("amuIgnored", "");
                if (!ignored.isEmpty()) {
                    for (String entry : ignored.split(",")) {
                        String[] temp = entry.split(":");
                        ignoredUpdates.put(Base64Coder.decodeString(temp[0]), Long.parseLong(temp[1]));
                        //the name of mod repo's are Base64 encoded to guarantee they wont interfere with my terrible string to hashmap implementation
                    }
                }
                //check for updates
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); //github timestamp to Epoch ( this is bad don't copy )
                for (var a : Vars.mods.list()) {
                    if (a.getRepo() != null && !a.hasSteamID()) { //if has repo and isnt from workshop
                        Http.get(ghApi + "/repos/" + a.getRepo() + "/releases/latest", res -> {
                            var json = Jval.read(res.getResultAsString());
                            long latest = df.parse(json.getString("published_at")).getTime();
                            long current = a.file.file().lastModified();
                            if (latest > current) {
                                if (!ignoredUpdates.containsKey(a.getRepo()) || ignoredUpdates.get(a.getRepo()) != latest) {
                                    BaseDialog dialog = new BaseDialog("Update Available");
                                    dialog.cont.add("'" + a.name + "' [white]has a new version available. Would you like to update?").row();
                                    dialog.cont.button("Yes", () -> {
                                        dialog.hide();
                                        importMod(a.getRepo(), a.isJava());
                                        Vars.ui.showInfo("Remember to delete the old mod file in order to finish updating.");
                                    });
                                    dialog.cont.row();
                                    dialog.cont.button("Ignore this update", () -> {
                                        dialog.hide();
                                        ignoredUpdates.put(a.getRepo(), latest);
                                    }).size(300f, 50f);
                                    dialog.keyDown(KeyCode.escape, dialog::hide);
                                    dialog.keyDown(KeyCode.back, dialog::hide);
                                    dialog.show();
                                }
                            }
                        });
                    } else {
                        Log.info("Mod named '" + a.name + "' does not have a valid github repository");
                    }
                }
            }
        });
    }
    private static void importMod(String repo, boolean isJava) {
        try {
            var method = ModsDialog.class.getDeclaredMethod("githubImportMod", String.class, boolean.class);
            method.setAccessible(true);
            method.invoke(Vars.ui.mods, repo, isJava);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    //menu formatter
    public static String[][] menuButtonFormatter(String input) {
        String[] rows = input.split("\n");
        String[][] out = new String[rows.length][];
        for (int r = 0; r < rows.length; r++) {
            out[r] = rows[r].split("\t");
        }
        return out;
    }

    public static void addBooleanGameSetting(String key, boolean defaultBooleanValue){
        Vars.ui.settings.game.checkPref(key, Core.settings.getBool(key, defaultBooleanValue));
    }
}
