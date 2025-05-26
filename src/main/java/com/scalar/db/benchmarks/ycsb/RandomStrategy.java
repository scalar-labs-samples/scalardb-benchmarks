package com.scalar.db.benchmarks.ycsb;

import java.util.Random;

/**
 * ランダム属性割り当て戦略
 * ユーザー属性とデータ属性をランダムに割り当てます
 */
public class RandomStrategy implements AttributeAssignmentStrategy {

    private final Random random;

    public RandomStrategy() {
        this.random = new Random();
    }

    public RandomStrategy(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public String assignUserAttribute(int userId, String[] attributeValues) {
        if (attributeValues == null || attributeValues.length == 0) {
            throw new IllegalArgumentException("Attribute values cannot be null or empty");
        }
        return attributeValues[random.nextInt(attributeValues.length)];
    }

    @Override
    public String assignDataAttribute(int recordId, String[] attributeValues) {
        if (attributeValues == null || attributeValues.length == 0) {
            throw new IllegalArgumentException("Attribute values cannot be null or empty");
        }
        return attributeValues[random.nextInt(attributeValues.length)];
    }
}
