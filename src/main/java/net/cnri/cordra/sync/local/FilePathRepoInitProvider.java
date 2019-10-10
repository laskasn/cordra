package net.cnri.cordra.sync.local;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;

import net.cnri.cordra.GsonUtility;
import net.cnri.cordra.model.RepoInit;
import net.cnri.cordra.sync.RepoInitProvider;

public class FilePathRepoInitProvider implements RepoInitProvider {

    private final Path path;

    public FilePathRepoInitProvider(Path path) {
        this.path = path;
    }

    @Override
    public RepoInit getRepoInit() throws Exception {
        if (path == null) return null;
        if (!Files.exists(path)) return null;
        try (
            InputStream fis = Files.newInputStream(path);
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            BufferedReader reader =  new BufferedReader(isr);
        ) {
            Gson gson = GsonUtility.getGson();
            RepoInit repoInit = gson.fromJson(reader, RepoInit.class);
            return repoInit;
        }
    }

    @Override
    public void cleanup() throws Exception {
        if (path != null) {
            if (Files.exists(path)) {
                Files.delete(path);
            }
        }
    }

}
