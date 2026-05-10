package com.safalifter.jobservice.enums;

import com.safalifter.jobservice.exc.IllegalStateTransitionException;
import com.safalifter.jobservice.model.Offer;

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

    public static Set<OfferStatus> getAvailableTransitions(OfferStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(
                ALLOWED_TRANSITIONS.getOrDefault(from, Collections.emptySet())
        );
    }

    public static boolean isTerminalState(OfferStatus status) {
        return status != null && ALLOWED_TRANSITIONS.getOrDefault(status, Collections.emptySet()).isEmpty();
    }

    public static void transition(Offer offer, OfferStatus targetStatus) {
        validateTransition(offer.getStatus(), targetStatus);
        offer.setStatus(targetStatus);
    }

    public static TransitionResult transitionWithResult(Offer offer, OfferStatus targetStatus) {
        OfferStatus fromStatus = offer.getStatus();
        validateTransition(fromStatus, targetStatus);
        offer.setStatus(targetStatus);
        return new TransitionResult(fromStatus, targetStatus, true);
    }

    public static class TransitionResult {
        private final OfferStatus fromStatus;
        private final OfferStatus toStatus;
        private final boolean success;

        public TransitionResult(OfferStatus fromStatus, OfferStatus toStatus, boolean success) {
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.success = success;
        }

        public OfferStatus getFromStatus() {
            return fromStatus;
        }

        public OfferStatus getToStatus() {
            return toStatus;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
