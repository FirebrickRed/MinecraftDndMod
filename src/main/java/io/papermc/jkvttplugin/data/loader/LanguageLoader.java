package io.papermc.jkvttplugin.data.loader;

import io.papermc.jkvttplugin.data.model.enums.LanguageRegistry;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;

public class LanguageLoader {
    private final JavaPlugin plugin;

    public LanguageLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadLanguages() {
        LanguageRegistry.clear();

        InputStream inputStream = plugin.getResource("languages.yml");
        if (inputStream == null) {
            plugin.getLogger().warning("languages.yml not found! Default languages will remain.");
            return;
        }

        Yaml yaml = new Yaml();
        Object data = yaml.load(inputStream);

        if (data instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> languages = (List<String>) data;
            LanguageRegistry.registerAll(languages);
            plugin.getLogger().info("Loaded " + languages.size() + " custom languages.");
        } else {
            plugin.getLogger().warning("langugaes.yml is not a list. Check formatting.");
        }
    }
}
