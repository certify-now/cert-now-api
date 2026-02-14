package com.uk.certifynow.certify_now.events;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public class BeforeDeleteNotification {

    private UUID id;

}
