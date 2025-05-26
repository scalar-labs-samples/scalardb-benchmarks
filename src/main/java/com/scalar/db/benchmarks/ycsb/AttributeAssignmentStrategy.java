package com.scalar.db.benchmarks.ycsb;

/**
 * ABAC属性割り当て戦略のインターフェース
 */
public interface AttributeAssignmentStrategy {

    /**
     * ユーザーに属性を割り当てる
     * 
     * @param userId          ユーザーID
     * @param attributeValues 利用可能な属性値の配列
     * @return 割り当てる属性値
     */
    String assignUserAttribute(int userId, String[] attributeValues);

    /**
     * データレコードに属性を割り当てる
     * 
     * @param recordId        レコードID
     * @param attributeValues 利用可能な属性値の配列
     * @return 割り当てる属性値
     */
    String assignDataAttribute(int recordId, String[] attributeValues);
}
