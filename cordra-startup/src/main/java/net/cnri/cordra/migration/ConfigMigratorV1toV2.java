package net.cnri.cordra.migration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.cnri.util.StreamTable;
import net.cnri.util.StringEncodingException;
import net.handle.hdllib.Util;

public class ConfigMigratorV1toV2 {

	private Path dataDir;
	private Path deleteMeDir;
	public static final String DELETE_ME_DIR = "migration_delete_me";
	
	public ConfigMigratorV1toV2(Path dataDir) {
		this.dataDir = dataDir;
	}
	
	public boolean needsToMigrate() {
		Path configDct = dataDir.resolve("config.dct");
		Path configJson = dataDir.resolve("config.json");
		if (Files.exists(configDct) && !Files.exists(configJson)) {
			return true;
		} else {
			return false;
		}
	}
	
	public void migrate() throws StringEncodingException, IOException {
		StreamTable configDct = readDct("config.dct");
		StreamTable passwordDct = readDct("password.dct");
		createRepoInitJson(configDct, passwordDct);
		configDctToConfigJson(configDct);
		moveOldFilesAndFoldersToDeleteMeDirectory();
	}
	
	private void createRepoInitJson(StreamTable configDct, StreamTable passwordDct) throws IOException  {
		JsonObject repoInitJson = new JsonObject();
		if (passwordDct != null) {
			String adminPassword = passwordDct.getStr("admin");
			repoInitJson.addProperty("adminPassword", adminPassword);
		}
		
		repoInitJson.addProperty("prefix", getPrefixFromConfigDct(configDct));
		repoInitJson.addProperty("handleAdminIdentity", getHandleAdminIdentity(configDct));
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String jsonString = gson.toJson(repoInitJson);
		Path repoInitJsonPath = dataDir.resolve("repoInit.json");
		try (BufferedWriter bw = Files.newBufferedWriter(repoInitJsonPath)) {
			bw.write(jsonString);
			bw.flush();
		}
	}
	
	private String getPrefixFromConfigDct(StreamTable configDct) {
		String serviceId = configDct.getStr("serviceid");
		return Util.getPrefixPart(serviceId);
	}

	private String getHandleAdminIdentity(StreamTable configDct) {
		String serviceId = configDct.getStr("serviceid");
		return "300:" + serviceId;
	}

	private StreamTable readDct(String dctName) throws StringEncodingException, IOException {
		Path configDctPath = dataDir.resolve(dctName);
		if (!Files.exists(configDctPath)) return null;
		File configDctFile = configDctPath.toFile();
		StreamTable dct = new StreamTable();
		dct.readFromFile(configDctFile);
		return dct;
	}
	
	private void configDctToConfigJson(StreamTable dct) throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("serverId", dct.getStr("serviceid"));
		
		int httpPort = dct.getInt("http_port", 8080);
		json.addProperty("httpPort", httpPort);
		
		if (dct.containsKey("https_port")) {
			int httpsPort = dct.getInt("https_port", 8443);
			json.addProperty("httpsPort", httpsPort);
		}
		
		String listenAddress = dct.getStr("listen_addr", "0.0.0.0");
		json.addProperty("listenAddress", listenAddress);
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String jsonString = gson.toJson(json);
		Path configJsonPath = dataDir.resolve("config.json");
		try (BufferedWriter bw = Files.newBufferedWriter(configJsonPath)) {
			bw.write(jsonString);
			bw.flush();
		}
	}
	
	private void moveOldFilesAndFoldersToDeleteMeDirectory() throws IOException {
		deleteMeDir = dataDir.resolve(DELETE_ME_DIR);
		if (!Files.exists(deleteMeDir)) {
			Files.createDirectory(deleteMeDir);
		}
		moveToDeleteMe("config.dct");
		moveToDeleteMe("password.dct"); 
		moveToDeleteMe("webapps-priority");
		moveToDeleteMe("webapps-temp");
		moveToDeleteMe("index");
		moveToDeleteMe("txns");
		moveToDeleteMe("knowbots");
		moveToDeleteMe("temp");
	}
	
	private void moveToDeleteMe(String source) throws IOException {
		Path sourcePath = dataDir.resolve(source);
		if (Files.exists(sourcePath)) {
			Files.move(sourcePath, deleteMeDir.resolve(source));
		}
	}
}
