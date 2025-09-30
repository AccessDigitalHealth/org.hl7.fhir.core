package entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ProfileComparison")
@Data
@NoArgsConstructor
public class ComparisonRow {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String leftPath;
  private Boolean leftMS;
  private int leftMin;
  private String leftMax;
  @Column(name = "leftType", length = 1000)
  private String leftType;
  @Column(name = "leftDescription", length = 1000)
  private String leftDescription;
  private String rightPath;
  private Boolean rightMS;
  @Column(name = "rightType", length = 1000)
  private String rightType;
  private int rightMin;
  private String rightMax;
  @Column(name = "rightDescription", length = 1000)
  private String rightDescription;
  private String breaks;
  private String otherBreaks;

}
