package com.scalar.db.benchmarks.ycsb;

import java.util.Random;

import com.scalar.db.api.Consistency;
import com.scalar.db.api.Get;
import com.scalar.db.api.Insert;
import com.scalar.db.api.Put;
import com.scalar.db.io.Key;
import com.scalar.db.io.TextColumn;
import com.scalar.kelpie.config.Config;

public class YcsbCommon {
  static final long DEFAULT_LOAD_CONCURRENCY = 1;
  static final long DEFAULT_LOAD_BATCH_SIZE = 1;
  static final long DEFAULT_RECORD_COUNT = 1000;
  static final long DEFAULT_PAYLOAD_SIZE = 1000;
  static final String NAMESPACE = "ycsb";
  static final String NAMESPACE_PRIMARY = "ycsb_primary"; // for multi-storage mode
  static final String NAMESPACE_SECONDARY = "ycsb_secondary"; // for multi-storage mode
  static final String TABLE = "usertable";
  static final String YCSB_KEY = "ycsb_key";
  static final String PAYLOAD = "payload";
  static final String CONFIG_NAME = "ycsb_config";
  static final String LOAD_CONCURRENCY = "load_concurrency";
  static final String LOAD_BATCH_SIZE = "load_batch_size";
  static final String LOAD_OVERWRITE = "load_overwrite";
  static final String RECORD_COUNT = "record_count";
  static final String PAYLOAD_SIZE = "payload_size";
  static final String OPS_PER_TX = "ops_per_tx";
  static final String USER_COUNT = "user_count"; // 新規追加: ユーザー数設定パラメータ
  // マルチユーザー認証用の共通パスワードベース
  static final String PASSWORD_BASE = "password";

  // ABAC関連の設定パラメータ
  static final String ABAC_ENABLED = "abac_enabled";
  static final String ABAC_ATTRIBUTE_TYPE = "abac_attribute_type";
  static final String ABAC_STRATEGY = "abac_strategy";
  static final String ABAC_ATTRIBUTE_VALUES = "abac_attribute_values";

  // ABAC属性タイプの定数
  static final String ATTRIBUTE_TYPE_LEVEL = "level";
  static final String ATTRIBUTE_TYPE_COMPARTMENT = "compartment";
  static final String ATTRIBUTE_TYPE_GROUP = "group";

  // ABAC戦略の定数
  static final String STRATEGY_RANDOM = "random";
  static final String STRATEGY_LOAD_BALANCED = "load_balanced";
  private static final int CHAR_START = 32; // [space]
  private static final int CHAR_STOP = 126; // [~]
  private static final char[] CHAR_SYMBOLS = new char[1 + CHAR_STOP - CHAR_START];

  static {
    for (int i = 0; i < CHAR_SYMBOLS.length; i++) {
      CHAR_SYMBOLS[i] = (char) (CHAR_START + i);
    }
  }

  private static final int[] FAST_MASKS = {
      554189328, // 10000
      277094664, // 01000
      138547332, // 00100
      69273666, // 00010
      34636833, // 00001
      346368330, // 01010
      727373493, // 10101
      588826161, // 10001
      935194491, // 11011
      658099827, // 10011
  };

  public static Get prepareGet(int key) {
    return prepareGet(NAMESPACE, TABLE, key);
  }

  public static Get prepareGet(String namespace, int key) {
    return prepareGet(namespace, TABLE, key);
  }

  public static Get prepareGet(String namespace, String table, int key) {
    return Get.newBuilder()
        .namespace(namespace)
        .table(table)
        .partitionKey(Key.ofInt(YCSB_KEY, key))
        .consistency(Consistency.LINEARIZABLE)
        .build();
  }

  public static Put preparePut(int key, String payload) {
    return preparePut(NAMESPACE, TABLE, key, payload);
  }

  public static Put preparePut(String namespace, int key, String payload) {
    return preparePut(namespace, TABLE, key, payload);
  }

  public static Put preparePut(String namespace, String table, int key, String payload) {
    return Put.newBuilder()
        .namespace(namespace)
        .table(table)
        .partitionKey(Key.ofInt(YCSB_KEY, key))
        .value(TextColumn.of(PAYLOAD, payload))
        .consistency(Consistency.LINEARIZABLE)
        .build();
  }

  public static Insert prepareInsert(int key, String payload) {
    return prepareInsert(NAMESPACE, TABLE, key, payload);
  }

  public static Insert prepareInsert(String namespace, int key, String payload) {
    return prepareInsert(namespace, TABLE, key, payload);
  }

  public static Insert prepareInsert(String namespace, String table, int key, String payload) {
    return Insert.newBuilder()
        .namespace(namespace)
        .table(table)
        .partitionKey(Key.ofInt(YCSB_KEY, key))
        .value(TextColumn.of(PAYLOAD, payload))
        .build();
  }

  public static Insert prepareInsertWithDataTag(int key, String payload, String dataTag) {
    return prepareInsertWithDataTag(NAMESPACE, TABLE, key, payload, dataTag);
  }

  public static Insert prepareInsertWithDataTag(String namespace, String table, int key, String payload,
      String dataTag) {
    return Insert.newBuilder()
        .namespace(namespace)
        .table(table)
        .partitionKey(Key.ofInt(YCSB_KEY, key))
        .value(TextColumn.of(PAYLOAD, payload))
        .value(TextColumn.of("data_tag", dataTag))
        .build();
  }

  public static int getLoadConcurrency(Config config) {
    return (int) config.getUserLong(CONFIG_NAME, LOAD_CONCURRENCY, DEFAULT_LOAD_CONCURRENCY);
  }

  public static int getLoadBatchSize(Config config) {
    return (int) config.getUserLong(CONFIG_NAME, LOAD_BATCH_SIZE, DEFAULT_LOAD_BATCH_SIZE);
  }

  public static boolean getLoadOverwrite(Config config) {
    return config.getUserBoolean(CONFIG_NAME, LOAD_OVERWRITE, false);
  }

  public static int getRecordCount(Config config) {
    return (int) config.getUserLong(CONFIG_NAME, RECORD_COUNT, DEFAULT_RECORD_COUNT);
  }

  public static int getPayloadSize(Config config) {
    return (int) config.getUserLong(CONFIG_NAME, PAYLOAD_SIZE, DEFAULT_PAYLOAD_SIZE);
  }

  // 新規追加: ユーザー数（スレッド数）取得メソッド
  public static int getUserCount(Config config) {
    long userCount = config.getUserLong(CONFIG_NAME, USER_COUNT, 0L);
    if (userCount <= 0) {
      // user_countが指定されていない場合は、concurrencyを使用
      userCount = config.getConcurrency();
    }
    return (int) userCount;
  }

  /**
   * 指定されたインデックスに対するユーザー名を生成します。
   * 
   * @param index ユーザーインデックス
   * @return ユーザー名
   */
  public static String getUserName(int index) {
    return "user" + index;
  }

  /**
   * 指定されたインデックスに対するパスワードを生成します。
   * 
   * @param index ユーザーインデックス
   * @return パスワード
   */
  public static String getPassword(int index) {
    return PASSWORD_BASE + index;
  }

  // ABAC設定取得メソッド
  public static boolean isAbacEnabled(Config config) {
    return config.getUserBoolean(CONFIG_NAME, ABAC_ENABLED, false);
  }

  public static String getAbacAttributeType(Config config) {
    return config.getUserString(CONFIG_NAME, ABAC_ATTRIBUTE_TYPE, ATTRIBUTE_TYPE_LEVEL);
  }

  public static String getAbacStrategy(Config config) {
    return config.getUserString(CONFIG_NAME, ABAC_STRATEGY, STRATEGY_RANDOM);
  }

  public static String[] getAbacAttributeValues(Config config) {
    String valuesStr = config.getUserString(CONFIG_NAME, ABAC_ATTRIBUTE_VALUES, "");
    if (valuesStr.isEmpty()) {
      // デフォルト値を属性タイプに応じて設定
      String attributeType = getAbacAttributeType(config);
      switch (attributeType) {
        case ATTRIBUTE_TYPE_LEVEL:
          return new String[] { "public", "confidential", "secret" };
        case ATTRIBUTE_TYPE_COMPARTMENT:
          return new String[] { "hr", "sales", "engineering" };
        case ATTRIBUTE_TYPE_GROUP:
          return new String[] { "team_a", "team_b", "team_c" };
        default:
          return new String[] { "public", "confidential", "secret" };
      }
    }
    return valuesStr.split(",");
  }

  /**
   * ABAC用のdata_tagフォーマットを生成します
   * フォーマット: level:compartments(カンマ区切り):groups(カンマ区切り)
   * 例: 'public::', 'confidential:hr,sales:', 'secret:hr:team_a,team_b'
   * 
   * @param level        レベル属性（nullの場合は空文字）
   * @param compartments コンパートメント属性のリスト（nullまたは空の場合は空文字）
   * @param groups       グループ属性のリスト（nullまたは空の場合は空文字）
   * @return 適切にフォーマットされたdata_tag文字列
   */
  public static String generateDataTag(String level, String[] compartments, String[] groups) {
    StringBuilder sb = new StringBuilder();

    // レベル部分
    if (level != null && !level.isEmpty()) {
      sb.append(level.toLowerCase());
    }
    sb.append(":");

    // コンパートメント部分
    if (compartments != null && compartments.length > 0) {
      for (int i = 0; i < compartments.length; i++) {
        if (i > 0) {
          sb.append(",");
        }
        sb.append(compartments[i].toLowerCase());
      }
    }
    sb.append(":");

    // グループ部分
    if (groups != null && groups.length > 0) {
      for (int i = 0; i < groups.length; i++) {
        if (i > 0) {
          sb.append(",");
        }
        sb.append(groups[i].toLowerCase());
      }
    }

    return sb.toString();
  }

  // This method is taken from benchbase.
  // https://github.com/cmu-db/benchbase/blob/bbe8c1db84ec81c6cdec6fbeca27b24b1b4e6612/src/main/java/com/oltpbenchmark/util/TextGenerator.java#L80
  public static char[] randomFastChars(Random rng, char[] chars) {
    // Ok so now the goal of this is to reduce the number of times that we have to
    // invoke a random number. We'll do this by grabbing a single random int
    // and then taking different bitmasks

    int num_rounds = chars.length / FAST_MASKS.length;
    int i = 0;
    for (int ctr = 0; ctr < num_rounds; ctr++) {
      int rand = rng.nextInt(10000); // CHAR_SYMBOLS.length);
      for (int mask : FAST_MASKS) {
        chars[i++] = CHAR_SYMBOLS[(rand | mask) % CHAR_SYMBOLS.length];
      }
    }
    // Use the old way for the remaining characters
    // I am doing this because I am too lazy to think of something more clever
    for (; i < chars.length; i++) {
      chars[i] = CHAR_SYMBOLS[rng.nextInt(CHAR_SYMBOLS.length)];
    }
    return (chars);
  }
}
