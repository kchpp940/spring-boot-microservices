package com.safalifter.authservice.orchestration;

import com.safalifter.authservice.client.adapter.NotificationServiceClientAdapter;
import com.safalifter.authservice.client.adapter.UserServiceClientAdapter;
import com.safalifter.authservice.dto.RegisterDto;
import com.safalifter.authservice.exc.GenericErrorResponse;
import com.safalifter.authservice.orchestration.compensation.CompensationStrategy;
import com.safalifter.authservice.orchestration.compensation.UserRollbackCompensationStrategy;
import com.safalifter.authservice.request.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationOrchestrator 编排链路测试")
class RegistrationOrchestratorTest {

    @Mock
    private UserServiceClientAdapter userServiceClientAdapter;

    @Mock
    private NotificationServiceClientAdapter notificationServiceClientAdapter;

    @Mock
    private CompensationStrategy mockCompensationStrategy;

    private RegistrationOrchestrator orchestrator;

    private RegistrationOrchestrator orchestratorWithRealRollback;

    private RegisterRequest testRequest;
    private RegisterDto testRegisterDto;

    @BeforeEach
    void setUp() {
        List<CompensationStrategy> strategies = new ArrayList<>();
        strategies.add(mockCompensationStrategy);
        orchestrator = new RegistrationOrchestrator(
                userServiceClientAdapter,
                notificationServiceClientAdapter,
                strategies
        );

        UserRollbackCompensationStrategy realRollbackStrategy = new UserRollbackCompensationStrategy(userServiceClientAdapter);
        List<CompensationStrategy> realStrategies = new ArrayList<>();
        realStrategies.add(realRollbackStrategy);
        orchestratorWithRealRollback = new RegistrationOrchestrator(
                userServiceClientAdapter,
                notificationServiceClientAdapter,
                realStrategies
        );

        testRequest = new RegisterRequest();
        testRequest.setUsername("testuser");
        testRequest.setPassword("Password123");
        testRequest.setEmail("test@example.com");

        testRegisterDto = RegisterDto.builder()
                .id("user-123")
                .username("testuser")
                .email("test@example.com")
                .build();
    }

    @Test
    @DisplayName("成功场景：完整链路成功执行")
    void orchestrate_whenAllStepsSucceed_shouldReturnRegisteredUser() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);
        doNothing().when(notificationServiceClientAdapter).save(any());

        RegisterDto result = orchestrator.orchestrate(testRequest);

        assertNotNull(result);
        assertEquals("user-123", result.getId());
        assertEquals("testuser", result.getUsername());

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, times(1)).save(any());
        verify(mockCompensationStrategy, never()).shouldApply(any());
        verify(mockCompensationStrategy, never()).compensate(any());
    }

    @Test
    @DisplayName("成功场景：调用顺序验证")
    void orchestrate_whenAllStepsSucceed_shouldCallInCorrectOrder() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);
        doNothing().when(notificationServiceClientAdapter).save(any());

        orchestrator.orchestrate(testRequest);

        InOrder inOrder = inOrder(userServiceClientAdapter, notificationServiceClientAdapter);
        inOrder.verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        inOrder.verify(notificationServiceClientAdapter, times(1)).save(any());
    }

    @Test
    @DisplayName("4xx 业务错误场景：user-service 返回 409 重复用户 - 不触发补偿（compensate 不执行）")
    void orchestrate_whenUserServiceReturns409_shouldNotTriggerCompensation() {
        GenericErrorResponse conflictError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.CONFLICT)
                .message("username already exists: testuser")
                .build();

        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenThrow(conflictError);

        GenericErrorResponse thrown = assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, thrown.getHttpStatus());
        assertTrue(thrown.getMessage().contains("USER_CREATION"));

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, never()).save(any());
        verify(mockCompensationStrategy, never()).compensate(any());
    }

    @Test
    @DisplayName("4xx 业务错误场景：user-service 返回 400 验证错误 - 不触发补偿（compensate 不执行）")
    void orchestrate_whenUserServiceReturns400_shouldNotTriggerCompensation() {
        GenericErrorResponse badRequestError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.BAD_REQUEST)
                .message("Invalid request")
                .build();

        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenThrow(badRequestError);

        GenericErrorResponse thrown = assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, thrown.getHttpStatus());

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, never()).save(any());
        verify(mockCompensationStrategy, never()).compensate(any());
    }

    @Test
    @DisplayName("4xx 业务错误场景：user-service 返回 404 - 不触发补偿（compensate 不执行）")
    void orchestrate_whenUserServiceReturns404_shouldNotTriggerCompensation() {
        GenericErrorResponse notFoundError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.NOT_FOUND)
                .message("Resource not found")
                .build();

        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenThrow(notFoundError);

        GenericErrorResponse thrown = assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, thrown.getHttpStatus());

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, never()).save(any());
        verify(mockCompensationStrategy, never()).compensate(any());
    }

    @Test
    @DisplayName("5xx 服务错误场景：user-service 返回 500 - 进入失败分支，不触发补偿（compensate 不执行）")
    void orchestrate_whenUserServiceReturns500_shouldEnterFailureBranch() {
        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Database error")
                .build();

        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenThrow(serverError);

        GenericErrorResponse thrown = assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, thrown.getHttpStatus());

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, never()).save(any());
        verify(mockCompensationStrategy, never()).compensate(any());
    }

    @Test
    @DisplayName("5xx 服务错误场景：user-service 返回 503 - 进入失败分支")
    void orchestrate_whenUserServiceReturns503_shouldEnterFailureBranch() {
        GenericErrorResponse unavailableError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .message("Service Unavailable")
                .build();

        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenThrow(unavailableError);

        GenericErrorResponse thrown = assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, thrown.getHttpStatus());

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, never()).save(any());
    }

    @Test
    @DisplayName("补偿场景：user-service 成功，notification-service 4xx 失败 - 触发补偿回滚用户")
    void orchestrate_whenNotificationServiceReturns4xx_shouldRollbackUser() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        GenericErrorResponse notFoundError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.NOT_FOUND)
                .message("Notification endpoint not found")
                .build();

        doThrow(notFoundError).when(notificationServiceClientAdapter).save(any());
        when(mockCompensationStrategy.shouldApply(any())).thenReturn(true);

        GenericErrorResponse thrown = assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, thrown.getHttpStatus());

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, times(1)).save(any());
        verify(mockCompensationStrategy, times(1)).shouldApply(any());
        verify(mockCompensationStrategy, times(1)).compensate(any());
    }

    @Test
    @DisplayName("补偿场景：user-service 成功，notification-service 5xx 失败 - 触发补偿回滚用户")
    void orchestrate_whenNotificationServiceReturns5xx_shouldRollbackUser() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Notification service failed")
                .build();

        doThrow(serverError).when(notificationServiceClientAdapter).save(any());
        when(mockCompensationStrategy.shouldApply(any())).thenReturn(true);

        GenericErrorResponse thrown = assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, thrown.getHttpStatus());

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, times(1)).save(any());
        verify(mockCompensationStrategy, times(1)).shouldApply(any());
        verify(mockCompensationStrategy, times(1)).compensate(any());
    }

    @Test
    @DisplayName("补偿调用顺序验证：notification 失败后触发补偿")
    void orchestrate_whenNotificationFails_shouldCallCompensationInCorrectOrder() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Notification service failed")
                .build();

        doThrow(serverError).when(notificationServiceClientAdapter).save(any());
        when(mockCompensationStrategy.shouldApply(any())).thenReturn(true);

        assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        InOrder inOrder = inOrder(
                userServiceClientAdapter,
                notificationServiceClientAdapter,
                mockCompensationStrategy
        );
        inOrder.verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        inOrder.verify(notificationServiceClientAdapter, times(1)).save(any());
        inOrder.verify(mockCompensationStrategy, times(1)).shouldApply(any());
        inOrder.verify(mockCompensationStrategy, times(1)).compensate(any());
    }

    @Test
    @DisplayName("不会重复创建用户：user-service 仅被调用一次")
    void orchestrate_whenFails_shouldNotRetryUserCreation() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Notification service failed")
                .build();

        doThrow(serverError).when(notificationServiceClientAdapter).save(any());
        when(mockCompensationStrategy.shouldApply(any())).thenReturn(true);

        assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("user-service 失败后不调用 notification-service")
    void orchestrate_whenUserServiceFails_shouldNotCallNotificationService() {
        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("User service failed")
                .build();

        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenThrow(serverError);

        assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        verify(notificationServiceClientAdapter, never()).save(any());
    }

    @Test
    @DisplayName("补偿策略不适用时不执行补偿")
    void orchestrate_whenCompensationStrategyNotApplicable_shouldNotCompensate() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Notification service failed")
                .build();

        doThrow(serverError).when(notificationServiceClientAdapter).save(any());
        when(mockCompensationStrategy.shouldApply(any())).thenReturn(false);

        assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        verify(mockCompensationStrategy, times(1)).shouldApply(any());
        verify(mockCompensationStrategy, never()).compensate(any());
    }

    @Test
    @DisplayName("异常场景：捕获非 GenericErrorResponse 的其他异常也应进入失败分支")
    void orchestrate_whenUnexpectedException_shouldHandleGracefully() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        doThrow(new RuntimeException("Unexpected error")).when(notificationServiceClientAdapter).save(any());
        when(mockCompensationStrategy.shouldApply(any())).thenReturn(true);

        GenericErrorResponse thrown = assertThrows(GenericErrorResponse.class, () ->
                orchestrator.orchestrate(testRequest));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, thrown.getHttpStatus());

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, times(1)).save(any());
        verify(mockCompensationStrategy, times(1)).compensate(any());
    }

    @Test
    @DisplayName("最小负例：notification-service 失败后，user rollback 只执行一次，且不会再次调用 userService.save")
    void orchestrate_whenNotificationFails_rollbackShouldExecuteOnceAndNotRetrySave() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Notification service failed")
                .build();

        doThrow(serverError).when(notificationServiceClientAdapter).save(any());
        doNothing().when(userServiceClientAdapter).internalHardDeleteUserById(anyString());

        assertThrows(GenericErrorResponse.class, () ->
                orchestratorWithRealRollback.orchestrate(testRequest));

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(notificationServiceClientAdapter, times(1)).save(any());
        verify(userServiceClientAdapter, times(1)).internalHardDeleteUserById("user-123");
        verify(userServiceClientAdapter, atMostOnce()).save(any(RegisterRequest.class));
        verify(userServiceClientAdapter, atMostOnce()).internalHardDeleteUserById("user-123");
    }

    @Test
    @DisplayName("真实回滚策略调用顺序验证：save → notification → internalHardDeleteUserById")
    void orchestrate_withRealRollbackStrategy_shouldCallInCorrectOrder() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Notification service failed")
                .build();

        doThrow(serverError).when(notificationServiceClientAdapter).save(any());
        doNothing().when(userServiceClientAdapter).internalHardDeleteUserById(anyString());

        assertThrows(GenericErrorResponse.class, () ->
                orchestratorWithRealRollback.orchestrate(testRequest));

        InOrder inOrder = inOrder(userServiceClientAdapter, notificationServiceClientAdapter);
        inOrder.verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        inOrder.verify(notificationServiceClientAdapter, times(1)).save(any());
        inOrder.verify(userServiceClientAdapter, times(1)).internalHardDeleteUserById("user-123");
    }

    @Test
    @DisplayName("真实回滚策略：验证 internalHardDeleteUserById 只被调用一次")
    void orchestrate_withRealRollbackStrategy_internalHardDeleteShouldBeCalledOnce() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Notification service failed")
                .build();

        doThrow(serverError).when(notificationServiceClientAdapter).save(any());
        doNothing().when(userServiceClientAdapter).internalHardDeleteUserById(anyString());

        assertThrows(GenericErrorResponse.class, () ->
                orchestratorWithRealRollback.orchestrate(testRequest));

        verify(userServiceClientAdapter, times(1)).internalHardDeleteUserById(anyString());
        verify(userServiceClientAdapter, atMostOnce()).internalHardDeleteUserById(anyString());
    }

    @Test
    @DisplayName("真实回滚策略：验证不会再次调用 userService.save")
    void orchestrate_withRealRollbackStrategy_shouldNotRetrySave() {
        when(userServiceClientAdapter.save(any(RegisterRequest.class)))
                .thenReturn(testRegisterDto);

        GenericErrorResponse serverError = GenericErrorResponse.builder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Notification service failed")
                .build();

        doThrow(serverError).when(notificationServiceClientAdapter).save(any());
        doNothing().when(userServiceClientAdapter).internalHardDeleteUserById(anyString());

        assertThrows(GenericErrorResponse.class, () ->
                orchestratorWithRealRollback.orchestrate(testRequest));

        verify(userServiceClientAdapter, times(1)).save(any(RegisterRequest.class));
        verify(userServiceClientAdapter, atMostOnce()).save(any(RegisterRequest.class));
    }
}
