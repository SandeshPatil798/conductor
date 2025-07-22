package com.netflix.conductor.common.model;

import com.netflix.conductor.common.metadata.tasks.Task;
import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.run.Workflow;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a workflow execution for synchronous response.
 */
@Data
@NoArgsConstructor
public class WorkflowRun {
  private String workflowId;
  private String requestId;
  private String correlationId;
  private Map<String, Object> input;
  private String createdBy;
  private long createTime;
  private Map<String, Object> output;
  private List<Task> tasks;
  private int priority;
  private Long updateTime;
  private Workflow.WorkflowStatus status;
  private Map<String, Object> variables;
}
