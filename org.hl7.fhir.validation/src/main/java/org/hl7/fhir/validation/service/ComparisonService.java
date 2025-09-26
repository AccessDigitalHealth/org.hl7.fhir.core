package org.hl7.fhir.validation.service;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  private static String findUSCoreComparison(Resource coreResource, String caCoreProfile) throws FileNotFoundException {
    String caTail = caCoreProfile.substring(caCoreProfile.lastIndexOf('/') + 1);
    String baseName = caTail.replace("-ca-core", "");
    InputStream is = ComparisonService.class.getResourceAsStream("/hl7.fhir.us.core-8.0.0-snapshots.tgz");
    if (is == null) {
      throw new FileNotFoundException("Resource not found in classpath!");
    }

      try (GZIPInputStream gis = new GZIPInputStream(is);
         TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {

      TarArchiveEntry entry;
      while ((entry = tis.getNextTarEntry()) != null) {
        String name = entry.getName();
        if (name.startsWith("package/StructureDefinition-") && name.endsWith(".json")) {
          System.out.println("Found StructureDefinition: " + name);
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
    String usCoreProfile = findUSCoreComparison(resLeft, left);
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