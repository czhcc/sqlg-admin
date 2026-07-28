package com.trs.modules.task.mapper;

import com.trs.modules.task.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步任务 MyBatis Mapper。
 *
 * @author czh
 * @date 2026/07/23
 */
@Mapper
public interface TaskMapper {

    void insert(Task task);

    int update(Task task);

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);

    int updateProgress(@Param("id") Long id,
                       @Param("progressPercent") Integer progressPercent,
                       @Param("processedCount") Long processedCount,
                       @Param("successCount") Long successCount,
                       @Param("failedCount") Long failedCount,
                       @Param("totalCount") Long totalCount,
                       @Param("currentStage") String currentStage,
                       @Param("lastHeartbeatTime") LocalDateTime lastHeartbeatTime);

    int updateResult(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("progressPercent") Integer progressPercent,
                     @Param("successCount") Long successCount,
                     @Param("failedCount") Long failedCount,
                     @Param("resultSummary") String resultSummary,
                     @Param("errorMessage") String errorMessage,
                     @Param("resultFilePath") String resultFilePath,
                     @Param("errorFilePath") String errorFilePath,
                     @Param("endTime") LocalDateTime endTime);

    Task selectById(@Param("id") Long id);

    Task selectByTaskNo(@Param("taskNo") String taskNo);

    List<Task> selectPage(@Param("offset") int offset,
                          @Param("size") int size,
                          @Param("status") String status,
                          @Param("category") String category,
                          @Param("taskType") String taskType,
                          @Param("connectionId") Long connectionId,
                          @Param("userId") Long userId,
                          @Param("schemaName") String schemaName,
                          @Param("targetLabel") String targetLabel,
                          @Param("keyword") String keyword,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    long selectCount(@Param("status") String status,
                     @Param("category") String category,
                     @Param("taskType") String taskType,
                     @Param("connectionId") Long connectionId,
                     @Param("userId") Long userId,
                     @Param("schemaName") String schemaName,
                     @Param("targetLabel") String targetLabel,
                     @Param("keyword") String keyword,
                     @Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);

    List<Task> selectStaleRunning(@Param("before") LocalDateTime before);

    int deleteById(@Param("id") Long id);

    long countByDateAndPrefix(@Param("datePrefix") String datePrefix,
                              @Param("typePrefix") String typePrefix,
                              @Param("taskType") String taskType);
}
