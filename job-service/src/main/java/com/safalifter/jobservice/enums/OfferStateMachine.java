package com.safalifter.jobservice.enums;

import com.safalifter.jobservice.exc.IllegalStateTransitionException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class OfferStateMachine {

    private static final Map<OfferStatus, Set<OfferStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OfferStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OfferStatus.OPEN, EnumSet.of(
                OfferStatus.ACCEPTED,
                OfferStatus.REJECTED,
                OfferStatus.WITHDRAWN,
                OfferStatus.EXPIRED,
                OfferStatus.CLOSED
        ));
        ALLOWED_TRANSITIONS.put(OfferStatus.CLOSED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(OfferStatus.ACCEPTED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(OfferStatus.REJECTED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(OfferStatus.WITHDRAWN, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(OfferStatus.EXPIRED, Collections.emptySet());
    }

    private OfferStateMachine() {
    }

    public static boolean isTransitionAllowed(OfferStatus from, OfferStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<OfferStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(from, Collections.emptySet());
        return allowedTargets.contains(to);
    }

    public static void validateTransition(OfferStatus from, OfferStatus to) {
        if (!isTransitionAllowed(from, to)) {
            throw new IllegalStateTransitionException(
                    String.format("Illegal state transition: %s -> %s", from, to)
            );
        }
    }
}
