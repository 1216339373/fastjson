package oracle.sql;

import java.sql.Timestamp;
import java.util.Date;

/**
 * 根据java.sql.Timestamp和java.util.Date制作oracle.sql.Timestamp
 * 2019年9月19日 上午10:25:49
 */
public class TIMESTAMP {

    private Date date;

    public TIMESTAMP(Timestamp date){
        this.date = date;
    }
    
    public Date toJdbc() {
        return date;
    }
}
