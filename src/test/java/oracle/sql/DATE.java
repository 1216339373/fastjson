package oracle.sql;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * 根据java.sql.Timestamp和java.sql.Date制作oracle.sql.Date
 * 2019年9月19日 上午10:25:15
 */
public class DATE {

    private Date date;

    public DATE(Timestamp date){
        long time = date.getTime();
        time = (time  / 1000) * 1000;
        this.date = new Date(time);
    }
    
    public Date toJdbc() {
        return date;
    }
}
