package com.trs.modules.graphAlgorithm;

import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.task.entity.Task;
import com.trs.modules.task.service.TaskService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import com.trs.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 算法结果序列化、任务归属、导出权限与文件边界测试。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
class GraphAlgorithmControllerTest {
    @TempDir Path directory;
    private final TaskService tasks = mock(TaskService.class);
    private final AlgorithmAccess access = mock(AlgorithmAccess.class);
    private final GraphAlgorithmController controller = new GraphAlgorithmController(tasks, access,
            mock(ConnectionVisibilityHelper.class), mock(SqlgGraphRegistry.class));

    private Task prepare() {
        User user = new User(); user.setId(7L); user.setRoles("READER");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        Task task = new Task(); task.setId(1L); task.setTaskType("GRAPH_ALGORITHM");
        task.setConnectionId(2L); task.setUserId(7L); task.setStatus(Task.STATUS_SUCCESS);
        task.setResultFilePath("result.json");
        when(tasks.getById(1L)).thenReturn(task);
        when(tasks.getStorageDir()).thenReturn(directory.toString());
        return task;
    }

    @AfterEach
    void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void resultUsesPlainMapCompatibleWithHttpJacksonVersion() throws Exception {
        prepare();
        Files.writeString(directory.resolve("result.json"), "{\"vertexCount\":6,\"rows\":[{\"id\":\"public.person:::1\",\"value\":3}]}");
        var body = controller.result(1L).getData();
        assertInstanceOf(Map.class, body);
        assertEquals(6, ((Map<?, ?>) body).get("vertexCount"));
        String json = new tools.jackson.databind.json.JsonMapper().writeValueAsString(controller.result(1L));
        assertTrue(json.contains("\"vertexCount\":6"));
        verify(access, times(2)).require(2L, "view");
    }

    @Test
    void otherUsersCannotReadOrExportTask() {
        prepare().setUserId(9L);
        assertThrows(SecurityException.class, () -> controller.result(1L));
        assertThrows(SecurityException.class, () -> controller.export(1L));
        verifyNoInteractions(access);
    }

    @Test
    void exportChecksIndependentPermission() throws Exception {
        prepare();
        doThrow(new SecurityException("export denied")).when(access).require(2L, "export");
        assertThrows(SecurityException.class, () -> controller.export(1L));
        verify(access).require(2L, "export");
    }

    @Test
    void resultCannotEscapeTaskStorageDirectory() {
        prepare().setResultFilePath("../outside.json");
        assertThrows(SecurityException.class, () -> controller.result(1L));
    }
}
