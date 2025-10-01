package org.hl7.fhir.r5.comparison;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonRowDTO {

  private String leftPath;
  private Boolean leftMS;
  private int leftMin;
  private String leftMax;
  private String leftType;
  private String leftDescription;

  private String rightPath;
  private Boolean rightMS;
  private int rightMin;
  private String rightMax;
  private String rightType;
  private String rightDescription;

  private String breaks;
  private String otherBreaks;

  private String uscorePath;
  private Integer uscoreMin;
  private String uscoreMax;
  private Boolean uscoreMS;
  private String uscoreDescription;
}
