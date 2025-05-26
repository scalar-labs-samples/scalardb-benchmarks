package com.scalar.db.benchmarks.ycsb;

/**
 * 負荷分散属性割り当て戦略
 * ユーザー属性とデータ属性を均等分散で割り当てます
 */
public class LoadBalancedStrategy implements AttributeAssignmentStrategy {

    @Override
    public String assignUserAttribute(int userId, String[] attributeValues) {
        if (attributeValues == null || attributeValues.length == 0) {
            throw new IllegalArgumentException("Attribute values cannot be null or empty");
        }
        // ユーザーIDを属性値の数で割った余りを使用して均等分散
        return attributeValues[userId % attributeValues.length];
    }

    @Override
    public String assignDataAttribute(int recordId, String[] attributeValues) {
        if (attributeValues == null || attributeValues.length == 0) {
            throw new IllegalArgumentException("Attribute values cannot be null or empty");
        }
        // レコードIDを属性値の数で割った余りを使用して均等分散
        return attributeValues[recordId % attributeValues.length];
    }
}
