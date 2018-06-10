 class Test {
    public int removeDuplicate(int[] a){
        int n = a.length;
        if(n <= 2){
            return n;
        }

        int index = 0;
        int occur = 0;
        for(int i = 1; i < n; ++i){
            if(a[index] != a[i]){
                occur = 0;
                a[++index] = a[i];
            }else{
                occur++;
                if(occur < 2){
                    a[++index] = a[i];
                }
            }
        }
        return index + 1;
    }
