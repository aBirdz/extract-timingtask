import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by jack4 on 2016/5/20.
 */
public class Task {


    private static final BasicDataSource ds = new BasicDataSource();


    private static String sourceTable = null;
    private static String targetTable = null;
    private static int insertNum = 0;
    private static int time_hour;
    private static int time_minute;
    private static int time_second;

    static {

        try (
                final InputStream is1
                        = Task.class.getClassLoader().getResourceAsStream("database.properties");

                final InputStream is2
                        = Task.class.getClassLoader().getResourceAsStream("config.properties")
        ) {

            Properties prop1 = new Properties();
            prop1.load(is1);
            ds.setUrl(prop1.getProperty("url"));
            ds.setDriverClassName(prop1.getProperty("driverClassName"));
            ds.setUsername(prop1.getProperty("username"));
            ds.setPassword(prop1.getProperty("password"));


            Properties prop2 = new Properties();
            prop2.load(is2);
            sourceTable = prop2.getProperty("sourceTable");
            targetTable = prop2.getProperty("targetTable");
            insertNum = Integer.parseInt(prop2.getProperty("insertNum"));
            time_hour = Integer.parseInt(prop2.getProperty("hour"));
            time_minute = Integer.parseInt(prop2.getProperty("minute"));
            time_second = Integer.parseInt(prop2.getProperty("second"));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {

        int time_total = time_hour * 60 * 60 * 1000
                + time_minute * 60 * 1000
                + time_second * 1000;
        //定时器
        Timer timer = new Timer();

        timer.schedule(new MyInsertTask(),
                0, time_total);


    }


    private static class MyInsertTask extends TimerTask {


        private int startNum = 0;
        private int endNum = startNum + insertNum;
        private List<Map<String, Object>> datas = new ArrayList<Map<String, Object>>();

        @Override
        public void run() {

            try {

                queryFromVehPassrec();
                // System.out.println(datas.size());
                // System.out.println(datas.toString());
                for (Map<String, Object> data : datas) {
                    //单条插入
                    try {
                        int count = insertToFlumeVehpass(data);
                        System.out.println(count);
                    } catch (SQLException e) {
                        System.out.println("插入异常 : " + e);

                    }
                }
                //System.out.println(datas.size());
                // System.out.println(datas);

                startNum += insertNum;
                endNum += insertNum;

            } catch (SQLException e) {
                System.out.println("数据操作异常:" + e.getMessage());
            }
        }

        private int insertToFlumeVehpass(Map<String, Object> data) throws SQLException {
            String fields = StringUtils.join(data.keySet(), ",");
            String[] ss = new String[data.size()];
            Arrays.fill(ss, "?");
            String val = StringUtils.join(ss, ",");
            final String preSql = "insert into " + targetTable + "(" + fields + ") values (" + val + ")";
            System.out.println(preSql);
            int i;
            try (Connection conn = ds.getConnection();
                 PreparedStatement statement = conn.prepareStatement(preSql)
            ) {
                int j = 0;
                for (Object value : data.values()) {
                    j++;
                    if (value instanceof Date) {
                        statement.setTimestamp(j, new Timestamp(((Date) value).getTime()));
                    } else statement.setObject(j, value);
                }
                    i = statement.executeUpdate();
            }
            return i;
        }


        private void queryFromVehPassrec() throws SQLException {
            String sql = "SELECT * FROM (SELECT rownum rn, t.* FROM " + sourceTable + " t WHERE rownum <="
                    + endNum + ")WHERE rn >" + startNum;

            try (
                    Connection conn = ds.getConnection();
                    Statement statement = conn.createStatement();
                    ResultSet resultSet = statement.executeQuery(sql)
            ) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                if (datas != null) {
                    datas.clear();
                }
                while (resultSet.next()) {
                    Map map = new HashMap();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                         // System.out.println(metaData.getColumnName(i));
                         // System.out.println(resultSet.getString(i));
                        //忽略rownum字段
                        if ("RN".equals(metaData.getColumnName(i))) continue;

                        map.put(metaData.getColumnName(i), resultSet.getString(i) == null ? " " : resultSet.getString(i));

                        if ("GCSJ".equals(metaData.getColumnName(i))
                                || "YRKSJ".equals(metaData.getColumnName(i))
                                || "RKSJ".equals(metaData.getColumnName(i))) {
                            map.put(metaData.getColumnName(i), new Date());
                        }
                    }
                    datas.add(map);
                }
            }
        }

    }
}
