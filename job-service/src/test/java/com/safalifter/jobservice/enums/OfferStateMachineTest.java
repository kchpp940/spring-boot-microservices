package com.safalifter.jobservice.enums;

import com.safalifter.jobservice.exc.IllegalStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfferStateMachineTest {

    @Test
    @DisplayName("OPEN -> ACCEPTED 应该是合法的状态转换")
    void testValidTransition_OpenToAccepted() {
        assertTrue(OfferStateMachine.isTransitionAllowed(OfferStatus.OPEN, OfferStatus.ACCEPTED));
        assertDoesNotThrow(() -> OfferStateMachine.validateTransition(OfferStatus.OPEN, OfferStatus.ACCEPTED));
    }

    @Test
    @DisplayName("OPEN -> REJECTED 应该是合法的状态转换")
    void testValidTransition_OpenToRejected() {
        assertTrue(OfferStateMachine.isTransitionAllowed(OfferStatus.OPEN, OfferStatus.REJECTED));
        assertDoesNotThrow(() -> OfferStateMachine.validateTransition(OfferStatus.OPEN, OfferStatus.REJECTED));
    }

    @Test
    @DisplayName("OPEN -> WITHDRAWN 应该是合法的状态转换")
    void testValidTransition_OpenToWithdrawn() {
        assertTrue(OfferStateMachine.isTransitionAllowed(OfferStatus.OPEN, OfferStatus.WITHDRAWN));
        assertDoesNotThrow(() -> OfferStateMachine.validateTransition(OfferStatus.OPEN, OfferStatus.WITHDRAWN));
    }

    @Test
    @DisplayName("OPEN -> EXPIRED 应该是合法的状态转换")
    void testValidTransition_OpenToExpired() {
        assertTrue(OfferStateMachine.isTransitionAllowed(OfferStatus.OPEN, OfferStatus.EXPIRED));
        assertDoesNotThrow(() -> OfferStateMachine.validateTransition(OfferStatus.OPEN, OfferStatus.EXPIRED));
    }

    @Test
    @DisplayName("ACCEPTED -> 任何状态 都应该是非法的")
    void testInvalidTransition_AcceptedToAny() {
        for (OfferStatus target : OfferStatus.values()) {
            assertFalse(OfferStateMachine.isTransitionAllowed(OfferStatus.ACCEPTED, target));
            final OfferStatus finalTarget = target;
            assertThrows(IllegalStateTransitionException.class,
                    () -> OfferStateMachine.validateTransition(OfferStatus.ACCEPTED, finalTarget));
        }
    }

    @Test
    @DisplayName("REJECTED -> 任何状态 都应该是非法的")
    void testInvalidTransition_RejectedToAny() {
        for (OfferStatus target : OfferStatus.values()) {
            assertFalse(OfferStateMachine.isTransitionAllowed(OfferStatus.REJECTED, target));
            final OfferStatus finalTarget = target;
            assertThrows(IllegalStateTransitionException.class,
                    () -> OfferStateMachine.validateTransition(OfferStatus.REJECTED, finalTarget));
        }
    }

    @Test
    @DisplayName("WITHDRAWN -> 任何状态 都应该是非法的")
    void testInvalidTransition_WithdrawnToAny() {
        for (OfferStatus target : OfferStatus.values()) {
            assertFalse(OfferStateMachine.isTransitionAllowed(OfferStatus.WITHDRAWN, target));
            final OfferStatus finalTarget = target;
            assertThrows(IllegalStateTransitionException.class,
                    () -> OfferStateMachine.validateTransition(OfferStatus.WITHDRAWN, finalTarget));
        }
    }

    @Test
    @DisplayName("EXPIRED -> 任何状态 都应该是非法的")
    void testInvalidTransition_ExpiredToAny() {
        for (OfferStatus target : OfferStatus.values()) {
            assertFalse(OfferStateMachine.isTransitionAllowed(OfferStatus.EXPIRED, target));
            final OfferStatus finalTarget = target;
            assertThrows(IllegalStateTransitionException.class,
                    () -> OfferStateMachine.validateTransition(OfferStatus.EXPIRED, finalTarget));
        }
    }

    @Test
    @DisplayName("null 状态转换应该是非法的")
    void testInvalidTransition_NullStates() {
        assertFalse(OfferStateMachine.isTransitionAllowed(null, OfferStatus.ACCEPTED));
        assertFalse(OfferStateMachine.isTransitionAllowed(OfferStatus.OPEN, null));
        assertFalse(OfferStateMachine.isTransitionAllowed(null, null));

        assertThrows(IllegalStateTransitionException.class,
                () -> OfferStateMachine.validateTransition(null, OfferStatus.ACCEPTED));
        assertThrows(IllegalStateTransitionException.class,
                () -> OfferStateMachine.validateTransition(OfferStatus.OPEN, null));
    }

    @Test
    @DisplayName("IllegalStateTransitionException 应该返回 409 CONFLICT 状态")
    void testIllegalStateTransitionException_HttpStatus() {
        IllegalStateTransitionException ex = new IllegalStateTransitionException("Test message");
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals("Test message", ex.getMessage());
    }
}
