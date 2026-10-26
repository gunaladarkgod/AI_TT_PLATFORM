package com.xgls.web.runner;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TrainResultSchemaCompatibilityRunnerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TrainResultSchemaCompatibilityRunner runner = new TrainResultSchemaCompatibilityRunner(jdbc);

    @Test
    void upgradesLegacySchemaAndDoesNotRepeatAlter() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1, 0);
        runner.run(null);
        runner.run(null);
        verify(jdbc, times(1)).execute(
                "ALTER TABLE `train_result` MODIFY COLUMN `task_id` INT NULL COMMENT '任务id；外部导入结果为空'");
    }

    @Test
    void skipsNullableOrAbsentColumn() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        runner.run(null);
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void reportsMigrationInstructionsWhenAlterFails() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        doThrow(new DataAccessResourceFailureException("ALTER denied")).when(jdbc).execute(anyString());
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner.run(null));
        assertTrue(error.getMessage().contains("V62"));
        assertTrue(error.getMessage().contains("ALTER"));
    }
}
