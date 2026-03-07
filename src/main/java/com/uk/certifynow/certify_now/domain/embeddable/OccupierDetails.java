package com.uk.certifynow.certify_now.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class OccupierDetails {

    @Column(name = "occupier_name")
    private String name;

    @Column(name = "occupier_telephone", length = 20)
    private String telephone;

    @Column(name = "occupier_email")
    private String email;

    @Column(name = "occupier_access_instructions", columnDefinition = "text")
    private String accessInstructions;
}
