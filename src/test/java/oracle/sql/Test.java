package oracle.sql;

public class Test {

	public static void main(String[] args) {
        for (int i=0;i<3;i++) {
        	System.out.println("-----------begin");
            if (i == 1) {
                System.out.println(i);
//                return;
                break;
            }
            System.out.println("-----------end");
        }
        System.out.println("-----------");
        char u1 = '1';
        char u2 = '1';
        char u3 = '1';
        char u4 = '1';
        System.out.println(new char[] { u1, u2, u3, u4 });
        System.out.println(new String(new char[] { u1, u2, u3, u4 }));
		System.out.println(Integer.parseInt(new String(new char[] { u1, u2, u3, u4 }), 16));
		System.out.println((char)0x1A);
		System.out.println((int) '/');
		System.out.println(Integer.MIN_VALUE / 10);
		// TODO Auto-generated method stub
		int a = 5;
		int b = 6;
		a |= b;
		System.out.println(a |= b);
		
		int i= 'f';
		int digit = i-'a';
		System.out.println(digit);
	}

}
