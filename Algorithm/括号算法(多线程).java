/*
 给定一个数字n,生成所有合法的括号表达式
 首先给一个动态规划的算法:
 F[n] = {j from 0 to n - 1, concat(F[j],F[n - 1 - j]))
 其中j代表有一个大括号,把左边的j个元素都封装起来了
 
 然后是并行计算, 对里循环进行并行化,开启多个线程,将循环分段计算
 */

class Solution {
    static class StringList {
        public ArrayList<String> s;
        public StringList(){
            s = new ArrayList<String>();
        }
        
        public void add(String val){
            s.add(val);
        }
        
        public void addAll(StringList other){
            s.addAll(other.s);
        }
        
        /*
         * 把lhs里面的每一个元素和rhs里面的每一个元素拼起来,生成新的元素
         * 同时lhs里面的每一个元素都要加上()
         * ["()","()()"] + ["(())"] => ["()(())","()()(())"]
         * */
        static  public StringList combine(StringList lhs, StringList rhs){
            StringList news = new StringList();
            for(String le : lhs.s){
                for(String re : rhs.s){
                    news.s.add("(" + le + ")" + re);
                }
            }
            return news;
        }
        
    }
    
    class Runner implements Runnable{
        final int i;
        final int start;
        final int end;
        final StringList result;
        final StringList[] s;
        
        public Runner(int i, int start, int end, StringList r,StringList[] s){
            {
                this.i = i;
                this.start = start;
                this.end = end;
                this.result = r;
                this.s = s;
            }
        }
        
        public void run(){
            for (int j = start; j < end; j++) {
                StringList tmp = StringList.combine(s[j],s[i - 1 - j]);
                synchronized (result) {
                    result.addAll(tmp);
                }
            }
        }
    }
    
    public List<String> generateParenthesis(int n) {
        // 这里尝试使用多线程进行优化, 开始4个线程
        
        final StringList[] s = new StringList[n + 1];
        for (int i = 0; i <= n; i++) {
            s[i] = new StringList();
        }
        
        s[0].add("");
        s[1].add("()");
        
        for (int i = 2; i <= n ; i++) {
            final int mid = i / 2;
            if(i >= 8){
                int threadNum = 6;
                // 采取多线程的模式
                Thread[] tArray = new Thread[threadNum];
                int step = i / threadNum;
                for(int k = 0; k < threadNum; k++) {
                    int start = k * step;
                    int end  = k == threadNum - 1 ? i : (k + 1) * step;
                    tArray[k] = new Thread(new Runner(i, start,end, s[i], s));
                    tArray[k].start();
                }
                try {
                    for(int k = 0; k < threadNum; k++) {
                        tArray[k].join();
                    }
                }catch (Exception e){
                }
            }else{
                for(int j = 0; j < i; j++){
                    s[i].addAll(StringList.combine(s[j],s[i - 1 - j]));
                }
            }
        }
        return s[n].s;
    }
}
