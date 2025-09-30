package entities;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "USCore")
public class USCoreComparisonRow {

  @Id
  private String path;
  private int min;
  private String max;
  private Boolean MS;
  @Column(name = "description", length = 1000)
  private String description;
}
