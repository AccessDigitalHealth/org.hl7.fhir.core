package org.hl7.fhir.validation.service;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Struct;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import ca.uhn.fhir.context.FhirContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.comparison.ComparisonRenderer;
import org.hl7.fhir.r5.comparison.ComparisonSession;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.i18n.RenderingI18nContext;
import org.hl7.fhir.validation.ValidationEngine;

@Slf4j
public class ComparisonService {

  private static final FhirContext ctx = FhirContext.forR4();

  private static final Map<String, String> usCoreToCoreProfileMapping = Map.ofEntries(
    Map.entry("http://fhir.infoway-inforoute.ca/cacore/StructureDefinition/observation-ca-core", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-simple-observation"),
    Map.entry("http://fhir.infoway-inforoute.ca/cacore/StructureDefinition/observation-laboratory-Pathology-ca-core", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-clinical-result"),
    Map.entry("http://fhir.infoway-inforoute.ca/cacore/StructureDefinition/observation-resultsradiology-ca-core", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-clinical-result"),
    Map.entry("http://fhir.infoway-inforoute.ca/cacore/StructureDefinition/observation-sexual-orientation", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-sexual-orientation"),
    Map.entry("http://fhir.infoway-inforoute.ca/cacore/StructureDefinition/observation-socialhistory-ca-core", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-simple-observation"),
    Map.entry("http://fhir.infoway-inforoute.ca/cacore/StructureDefinition/observation-tobaccouse-ca-core", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-smokingstatus"),
    Map.entry("http://fhir.infoway-inforoute.ca/cacore/StructureDefinition/condition-ca-core", "http://hl7.org/fhir/us/core/StructureDefinition/us-core-condition-problems-health-concerns")
  );
  private static String findUSCoreComparison(String caCoreProfile) throws FileNotFoundException {
    String caTail = caCoreProfile.substring(caCoreProfile.lastIndexOf('/') + 1);
    String baseName = caTail.replace("-ca-core", "");
    InputStream is = ComparisonService.class.getResourceAsStream("/hl7.fhir.us.core-8.0.0-snapshots.tgz");

    if (is == null) {
      throw new FileNotFoundException("Resource not found in classpath!");
    }

    if (usCoreToCoreProfileMapping.containsKey(caCoreProfile)) return usCoreToCoreProfileMapping.get(caCoreProfile);

    try (GZIPInputStream gis = new GZIPInputStream(is);
       TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {

      TarArchiveEntry entry;
      while ((entry = tis.getNextTarEntry()) != null) {
        String name = entry.getName();
        if (name.startsWith("package/StructureDefinition-") && name.endsWith(".json")) {
          org.hl7.fhir.r4.model.StructureDefinition sd = (org.hl7.fhir.r4.model.StructureDefinition) ctx.newJsonParser().parseResource(tis);
          String url = sd.getUrl();
          if (url.contains("us-core-" + baseName)) {
            return url;
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return null;
  }

  public static void doFullIgLeftRightComparison(ValidationEngine validationEngine, String dest, String leftIg, String rightIg) throws EOperationOutcome, IOException {

    List<StructureDefinition> coreStructureDefinitions = validationEngine.getContext()
      .listStructures()
      .stream()
      .filter(sd -> sd.getSourcePackage() != null &&
        leftIg.equals(sd.getSourcePackage().getId() + "#" + sd.getSourcePackage().getVersion()))
      .collect(Collectors.toList());

    List<StructureDefinition> targetStructureDefinitions = validationEngine.getContext()
      .listStructures()
      .stream()
      .filter(sd -> sd.getSourcePackage() != null &&
        rightIg.equals(sd.getSourcePackage().getId() + "#" + sd.getSourcePackage().getVersion()))
      .collect(Collectors.toList());

    for (StructureDefinition sd : coreStructureDefinitions){
      String coreProfileId = sd.getId();
      String targetProfile = mapCoreProfileToTargetProfile(coreProfileId, targetStructureDefinitions, validationEngine);
      if (targetProfile != null) log.info("Core: {} ----- Target: {}", coreProfileId, targetProfile);
      doLeftRightComparison(coreProfileId, targetProfile, dest, validationEngine);
    }
  }

  private static String mapCoreProfileToTargetProfile(String coreProfileId, List<StructureDefinition> targetStructures, ValidationEngine validationEngine){

    StructureDefinition coreStructure = (StructureDefinition) validationEngine.getContext().fetchResource(Resource.class, coreProfileId);

    for (StructureDefinition sd: targetStructures){
      String caCoreBaseName = coreProfileId.replace("-ca-core", "");
      if (sd.getUrl().toUpperCase().contains(caCoreBaseName.toUpperCase())){
        if (sd.getType().equals(coreStructure.getType()) && !Objects.equals(sd.getType(), "Extension")){
          return sd.getUrl();
        }
      }
    }
    return null;
  }

  public static void doLeftRightComparison(String left, String right, String dest, ValidationEngine validator) throws IOException, FHIRException, EOperationOutcome {
    // ok now set up the comparison
    Resource resLeft = validator.getContext().fetchResource(Resource.class, left);
    Resource resRight = validator.getContext().fetchResource(Resource.class, right);
    if (resLeft == null) {
      log.warn("Unable to locate left resource " + left);
    }
    if (resRight == null) {
      log.warn("Unable to locate right resource " + right);
    }
    String usCoreProfile = findUSCoreComparison(left);
    if (usCoreProfile != null && resLeft!= null){
      Resource usCoreResource = validator.getContext().fetchResource(Resource.class, usCoreProfile);
      if (resLeft instanceof StructureDefinition && resRight instanceof StructureDefinition) {
        ComparisonService.compareStructureDefinitions(dest, validator, left, usCoreProfile, (StructureDefinition) resLeft, (StructureDefinition) usCoreResource);
      }
    }
    if (resLeft != null && resRight != null) {
      if (resLeft instanceof StructureDefinition && resRight instanceof StructureDefinition) {
        ComparisonService.compareStructureDefinitions(dest, validator, left, right, (StructureDefinition) resLeft, (StructureDefinition) resRight);
      } else if (resLeft instanceof CapabilityStatement && resRight instanceof CapabilityStatement) {
        ComparisonService.compareCapabilityStatements(dest, validator, left, right, (CanonicalResource) resLeft, (CanonicalResource) resRight);
      } else
        log.warn("Unable to compare left resource " + left + " (" + resLeft.fhirType() + ") with right resource " + right + " (" + resRight.fhirType() + ")");
    }
  }

  public static void compareCapabilityStatements(String dest, ValidationEngine validator, String left, String right, CanonicalResource resLeft, CanonicalResource resRight) throws IOException {
    throw new Error("CapabilityStatement comparison is not implemented at this time (WIP)");
//    ComparisonSession session = new ComparisonSession(validator.getContext(), validator.getContext(), "Comparing Capability Statements", null);
//    session.compare(resLeft, resRight);
//    ComparisonRenderer cr = new ComparisonRenderer(validator.getContext(), validator.getContext(), dest, session);
//    cr.getTemplates().put("CodeSystem", new String(validator.getContext().getBinaries().get("template-comparison-CodeSystem.html")));
//    cr.getTemplates().put("ValueSet", new String(validator.getContext().getBinaries().get("template-comparison-ValueSet.html")));
//    cr.getTemplates().put("Profile", new String(validator.getContext().getBinaries().get("template-comparison-Profile.html")));
//    cr.getTemplates().put("Index", new String(validator.getContext().getBinaries().get("template-comparison-index.html")));
//    File htmlFile = cr.render(left, right);
//    Desktop.getDesktop().browse(htmlFile.toURI());
//    cr.getTemplates().put("CapabilityStatement", new String(context.getBinaries().get("template-comparison-CapabilityStatement.html")));
  }

  public static void compareStructureDefinitions(String dest, ValidationEngine validator, String left, String right, StructureDefinition resLeft, StructureDefinition resRight) throws IOException, FHIRException, EOperationOutcome {
    log.info("Comparing StructureDefinitions " + left + " to " + right);
    ComparisonSession session = new ComparisonSession(new RenderingI18nContext(), validator.getContext(), validator.getContext(), "Comparing Profiles", null, null);
    session.compare(resLeft, resRight);

    log.info("Generating output to " + dest + "...");
    FileUtilities.createDirectory(dest);
    ComparisonRenderer cr = new ComparisonRenderer(validator.getContext(), validator.getContext(), dest, session);
    cr.loadTemplates(validator.getContext());
    File htmlFile = cr.render(left, right);
    // only try to open in browser if not in headless mode
    if (!GraphicsEnvironment.isHeadless()) {
      try {
        Desktop.getDesktop().browse(htmlFile.toURI());
      } catch (UnsupportedOperationException | IOException e) {
        log.error("Unable to open browser: " + e.getMessage());
      }
    } else {
      log.info("Headless environment detected; skipping browser launch.");
    }
    log.info("Done: " + htmlFile.toURI());
  }

}