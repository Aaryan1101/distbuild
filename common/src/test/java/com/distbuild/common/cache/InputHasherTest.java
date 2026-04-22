package com.distbuild.common.cache;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

public class InputHasherTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testSameInputsProduceSameHash() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        InputHasher hasher = new InputHasher(projectRoot);

        // Create test files
        Path sourceFile1 = createTestFile(projectRoot.resolve("src/Main.java"), "public class Main {}");
        Path sourceFile2 = createTestFile(projectRoot.resolve("src/Util.java"), "public class Util {}");

        InputHasher.ModuleInputs inputs1 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .addSourceFile("src/Util.java")
                .javaVersion("17")
                .build();

        InputHasher.ModuleInputs inputs2 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .addSourceFile("src/Util.java")
                .javaVersion("17")
                .build();

        String hash1 = hasher.computeHash(inputs1);
        String hash2 = hasher.computeHash(inputs2);

        assertEquals("Same inputs should produce same hash", hash1, hash2);
    }

    @Test
    public void testDifferentSourceFilesProduceDifferentHashes() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        InputHasher hasher = new InputHasher(projectRoot);

        Path sourceFile1 = createTestFile(projectRoot.resolve("src/Main.java"), "public class Main {}");
        Path sourceFile2 = createTestFile(projectRoot.resolve("src/Main2.java"), "public class Main { int x; }");

        InputHasher.ModuleInputs inputs1 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .javaVersion("17")
                .build();

        InputHasher.ModuleInputs inputs2 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main2.java")
                .javaVersion("17")
                .build();

        String hash1 = hasher.computeHash(inputs1);
        String hash2 = hasher.computeHash(inputs2);

        assertNotEquals("Different source files should produce different hashes", hash1, hash2);
    }

    @Test
    public void testDifferentJavaVersionProducesDifferentHash() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        InputHasher hasher = new InputHasher(projectRoot);

        Path sourceFile = createTestFile(projectRoot.resolve("src/Main.java"), "public class Main {}");

        InputHasher.ModuleInputs inputs1 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .javaVersion("17")
                .build();

        InputHasher.ModuleInputs inputs2 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .javaVersion("11")
                .build();

        String hash1 = hasher.computeHash(inputs1);
        String hash2 = hasher.computeHash(inputs2);

        assertNotEquals("Different Java versions should produce different hashes", hash1, hash2);
    }

    @Test
    public void testCompilerOptionsAffectHash() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        InputHasher hasher = new InputHasher(projectRoot);

        Path sourceFile = createTestFile(projectRoot.resolve("src/Main.java"), "public class Main {}");

        InputHasher.ModuleInputs inputs1 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .javaVersion("17")
                .addCompilerOption("debug", "true")
                .build();

        InputHasher.ModuleInputs inputs2 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .javaVersion("17")
                .addCompilerOption("debug", "false")
                .build();

        String hash1 = hasher.computeHash(inputs1);
        String hash2 = hasher.computeHash(inputs2);

        assertNotEquals("Different compiler options should produce different hashes", hash1, hash2);
    }

    @Test
    public void testJarHashesAffectHash() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        InputHasher hasher = new InputHasher(projectRoot);

        Path sourceFile = createTestFile(projectRoot.resolve("src/Main.java"), "public class Main {}");

        InputHasher.ModuleInputs inputs1 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .javaVersion("17")
                .addJarHash("lib/utils.jar", "hash1")
                .build();

        InputHasher.ModuleInputs inputs2 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .javaVersion("17")
                .addJarHash("lib/utils.jar", "hash2")
                .build();

        String hash1 = hasher.computeHash(inputs1);
        String hash2 = hasher.computeHash(inputs2);

        assertNotEquals("Different JAR hashes should produce different hashes", hash1, hash2);
    }

    @Test
    public void testJarHashComputation() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        InputHasher hasher = new InputHasher(projectRoot);

        // Create a test JAR file
        Path jarFile = createTestFile(projectRoot.resolve("lib/test.jar"), "fake jar content");

        String hash1 = hasher.computeJarHash(jarFile);
        String hash2 = hasher.computeJarHash(jarFile);

        assertEquals("Same JAR should produce same hash", hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 produces 64 character hex string
    }

    @Test(expected = IOException.class)
    public void testMissingJarFileThrowsException() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        InputHasher hasher = new InputHasher(projectRoot);

        Path nonExistentJar = projectRoot.resolve("lib/nonexistent.jar");
        hasher.computeJarHash(nonExistentJar);
    }

    @Test
    public void testMultipleJarHashes() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        InputHasher hasher = new InputHasher(projectRoot);

        Path jar1 = createTestFile(projectRoot.resolve("lib/jar1.jar"), "content1");
        Path jar2 = createTestFile(projectRoot.resolve("lib/jar2.jar"), "content2");

        Set<String> jarPaths = Set.of("lib/jar1.jar", "lib/jar2.jar");
        Map<String, String> hashes = hasher.computeJarHashes(jarPaths);

        assertEquals(2, hashes.size());
        assertTrue(hashes.containsKey("lib/jar1.jar"));
        assertTrue(hashes.containsKey("lib/jar2.jar"));
        assertNotEquals(hashes.get("lib/jar1.jar"), hashes.get("lib/jar2.jar"));
    }

    @Test
    public void testIncrementalStateAffectsHash() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        InputHasher hasher = new InputHasher(projectRoot);

        Path sourceFile = createTestFile(projectRoot.resolve("src/Main.java"), "public class Main {}");

        InputHasher.ModuleInputs inputs1 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .javaVersion("17")
                .incrementalState("state1".getBytes())
                .build();

        InputHasher.ModuleInputs inputs2 = InputHasher.ModuleInputs.builder()
                .moduleName("test-module")
                .addSourceFile("src/Main.java")
                .javaVersion("17")
                .incrementalState("state2".getBytes())
                .build();

        String hash1 = hasher.computeHash(inputs1);
        String hash2 = hasher.computeHash(inputs2);

        assertNotEquals("Different incremental state should produce different hashes", hash1, hash2);
    }

    private Path createTestFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.write(path, content.getBytes());
    }
}
