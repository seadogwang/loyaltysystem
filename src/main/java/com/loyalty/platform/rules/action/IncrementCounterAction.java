package com.loyalty.platform.rules.action;

/**
 * 计数器动作 — 更新会员 ext_attributes 中的计数器变量。
 *
 * <p>设计文档 §4.2: 变量存储在 member.ext_attributes JSONB 中，
 * 通过 MemberVariableService 更新。计数器是用户自定义的动态变量，
 * 每次规则触发时按指定的步长累加或递减。
 */
public class IncrementCounterAction extends Action {
    private final String programCode;
    private final String memberId;
    private final String counterName;
    private final String operator; // "+" or "-"
    private final double step;
    private final double startValue;

    public IncrementCounterAction(String programCode, String memberId, String counterName,
                                   String operator, double step, double startValue,
                                   String ruleId, String ruleSnapshotId) {
        super(ruleId, ruleSnapshotId);
        this.programCode = programCode;
        this.memberId = memberId;
        this.counterName = counterName;
        this.operator = operator;
        this.step = step;
        this.startValue = startValue;
    }

    public String getProgramCode() { return programCode; }
    public String getMemberId() { return memberId; }
    public String getCounterName() { return counterName; }
    public String getOperator() { return operator; }
    public double getStep() { return step; }
    public double getStartValue() { return startValue; }

    @Override public String actionType() { return "INCREMENT_COUNTER"; }
    @Override public String toString() { return "IncrementCounter{" + counterName + " " + operator + " " + step + "}"; }
}