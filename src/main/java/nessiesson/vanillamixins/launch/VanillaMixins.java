package nessiesson.vanillamixins.launch;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import net.minecraft.launchwrapper.Launch;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class VanillaMixins {
	// Definitely not stolen from the SpongeVanilla codebase at all.
	// >_>
	// https://github.com/SpongePowered/SpongeVanilla/blob/2dba56156d8c4f515adb37105a78b6d58874c6e6/src/main/java/org/spongepowered/server/launch/VanillaServerMain.java

	private static final String LIBRARIES_DIR = "libraries";

	private static final String MINECRAFT_SERVER_VERSION = "@MCVERSION@";
	private static final String MINECRAFT_SERVER_LOCAL = "minecraft_server." + MINECRAFT_SERVER_VERSION + ".jar";
	private static final String MINECRAFT_MANIFEST_REMOTE = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

	private static final String LAUNCHWRAPPER_PATH = "/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar";
	private static final String LAUNCHWRAPPER_LOCAL = LIBRARIES_DIR + LAUNCHWRAPPER_PATH;
	private static final String LAUNCHWRAPPER_REMOTE = "https://libraries.minecraft.net" + LAUNCHWRAPPER_PATH;
	private static final String LAUNCHWRAPPER_SHA1 = "111e7bea9c968cdb3d06ef4632bf7ff0824d0f36";

	private static final String TWEAK_ARGUMENT = "--tweakClass";
	private static final String TWEAKER = "@TWEAKER@";

	public static void main(String[] args) throws Exception {
		// Download/verify Minecraft server installation if necessary and not disabled
		// Get the location of our jar
		Path base = Paths.get(nessiesson.vanillamixins.launch.VanillaMixins.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();

		try {
			// Download dependencies
			if (!downloadMinecraft(base)) {
				System.err.println("Failed to load all required dependencies. Please download them manually:");
				System.err.println("Download the Minecraft server version " + MINECRAFT_SERVER_VERSION + " and copy it to "
						+ base.resolve(MINECRAFT_SERVER_LOCAL).toAbsolutePath());
				System.err.println("Download " + LAUNCHWRAPPER_REMOTE + " and copy it to "
						+ base.resolve(LAUNCHWRAPPER_LOCAL).toAbsolutePath());
				System.exit(1);
				return;
			}
		} catch (IOException e) {
			System.err.println("Failed to download required dependencies. Please try again later.");
			e.printStackTrace();
			System.exit(1);
			return;
		}

		Launch.main(getLaunchArguments(TWEAKER, new ArrayList<>()));
	}

	private static String[] getLaunchArguments(String primaryTweaker, List<String> tweakers) {
		if (tweakers.isEmpty()) {
			return new String[]{TWEAK_ARGUMENT, primaryTweaker};
		}

		String[] result = new String[tweakers.size() * 2 + 2];
		result[0] = TWEAK_ARGUMENT;
		result[1] = primaryTweaker;

		int i = 2;
		for (String tweaker : tweakers) {
			result[i++] = TWEAK_ARGUMENT;
			result[i++] = tweaker;
		}

		return result;
	}

	private static boolean downloadMinecraft(Path base) throws IOException, NoSuchAlgorithmException {
		// Make sure the Minecraft server is available, or download it otherwise
		Path path = base.resolve(MINECRAFT_SERVER_LOCAL);
		if (Files.notExists(path)) {
			System.out.println("Downloading the versions manifest...");

			// Download the file with all of the Minecraft versions information
			JsonValue versions = downloadJson(MINECRAFT_MANIFEST_REMOTE);

			String versionManifestRemote = null;

			// Find the current version manifest URL
			for (JsonValue versionInfo : versions.asObject().get("versions").asArray()) {
				JsonObject obj = versionInfo.asObject();

				String versionId = obj.get("id").asString();
				if (versionId.equals(MINECRAFT_SERVER_VERSION)) {
					versionManifestRemote = obj.get("url").asString();
					break;
				}
			}


			if (versionManifestRemote == null) {
				throw new NoSuchElementException("Could not find " + MINECRAFT_SERVER_VERSION + "'s manifest URL");
			}

			JsonValue versionManifest = downloadJson(versionManifestRemote);
			JsonObject serverObj = versionManifest.asObject()
					.get("downloads").asObject()
					.get("server").asObject();

			// Find the server URL and SHA-1 digest
			String serverRemote = serverObj.get("url").asString();
			String sha1 = serverObj.get("sha1").asString();

			downloadAndVerify(serverRemote, path, sha1);
		}

		path = base.resolve(LAUNCHWRAPPER_LOCAL);

		if (!Files.exists(path)) {
			// Make sure Launchwrapper is available, or download it otherwise
			downloadAndVerify(LAUNCHWRAPPER_REMOTE, path, LAUNCHWRAPPER_SHA1);
		}

		return true;
	}

	private static JsonValue downloadJson(String remote) throws IOException {
		URL url = new URL(remote);

		try (InputStreamReader reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
			return Json.parse(reader);
		}
	}

	/**
	 * Downloads a file and verify its digest.
	 *
	 * @param remote   The file URL
	 * @param path     The local path
	 * @param expected The SHA-1 expected digest
	 * @throws IOException              If there is a problem while downloading the file
	 * @throws NoSuchAlgorithmException Never because the JVM is required to support SHA-1
	 */
	private static void downloadAndVerify(String remote, Path path, String expected) throws IOException, NoSuchAlgorithmException {
		Files.createDirectories(path.getParent());

		String name = path.getFileName().toString();
		URL url = new URL(remote);

		System.out.println("Downloading " + name + "... This can take a while.");
		System.out.println(url);

		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

		// Pipe the download stream into the file and compute the SHA-1
		try (DigestInputStream stream = new DigestInputStream(url.openStream(), sha1);
		     ReadableByteChannel in = Channels.newChannel(stream);
		     FileChannel out = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			out.transferFrom(in, 0, Long.MAX_VALUE);
		}

		String fileSha1 = toHexString(sha1.digest());

		if (expected.equals(fileSha1)) {
			System.out.println("Successfully downloaded " + name + " and verified checksum!");
		} else {
			Files.delete(path);
			throw new IOException("Checksum verification failed: Expected " + expected + ", got " + fileSha1);
		}
	}

	// From http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
	private static final char[] hexArray = "0123456789abcdef".toCharArray();

	private static String toHexString(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

}
