import ca.uhn.fhir.context.FhirContext;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.hl7.fhir.r5.model.StructureDefinition;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;


public class MultiComparisonWrapper {

  private static final FhirContext ctx = FhirContext.forR4();

  public static void main (String[] args){

  }

  private void extractAndMatchIgFiles(String caCoreProfile){
    Path igFile = Paths.get("resources/.tgz");

    try (InputStream fis = Files.newInputStream(igFile);
         GZIPInputStream gis = new GZIPInputStream(fis);
         TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {

      TarArchiveEntry entry;
      while ((entry = tis.getNextTarEntry()) != null) {
        String name = entry.getName();
        if (name.startsWith("package/StructureDefinition-") && name.endsWith(".json")) {
          System.out.println("Found StructureDefinition: " + name);

          StructureDefinition sd = (StructureDefinition) ctx.newJsonParser().parseResource(tis);

        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void runValidatorCli(Path jarFile, String... params) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add("java");
    command.add("-jar");
    command.add(jarFile.toAbsolutePath().toString());
    command.addAll(Arrays.asList(params));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.inheritIO();
    Process p = pb.start();
    int exit = p.waitFor();
    System.out.println("Finished run");
  }

  private Path loadValidatorCli() throws IOException {
    InputStream in = MultiComparisonWrapper.class.getResourceAsStream("/lib/validator_cli.jar");
    if (in == null) throw new FileNotFoundException();
    Path tempFile = Files.createTempFile("embedded-cli-validator", ".jar");
    tempFile.toFile().deleteOnExit();
    try (OutputStream out = Files.newOutputStream(tempFile)) {
      in.transferTo(out);
    }
    return tempFile;
  }
}
