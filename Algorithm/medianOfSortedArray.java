/*
 这题目的关键是选取 k/2的位置, 然后可以判断至少有一半是可以舍弃的
 这里需要注意,必须选取 k/2和较小数组大小的上限
 为了比较起来方便,需要定义 k是数量还是index,这里定义为数量比较方便
 因为需要从原来的数量里面减去 k/2
 */



class Solution {
    public double findMedianSortedArrays(int[] nums1, int[] nums2) {
        int m = nums1.length;
        int n = nums2.length;
        int total = m + n ;
        
        if(total % 2 == 1){
            // 奇数,只需要中间的那一个
            int mid = (total + 1) / 2;
            return find(mid,nums1,nums2,0,0);
        }else{
            // 偶数,需要两个
            int mid1 = total / 2;
            int mid2 = mid1 + 1;
            double r1 = find(mid1,nums1,nums2,0,0);
            double r2 = find(mid2,nums1,nums2,0,0);
            return (r1 + r2) / 2;
        }
    }
    
    int find(int k, int[] A, int[] B,int aStart,int bStart){
        int m = A.length - aStart;
        int n = B.length - bStart;
        
        if(m > n) {
            return find(k,B,A,bStart,aStart);
        }
        
        if(m == 0) {
            return B[k - 1];
        }
        if(k == 1){
            return Math.min(A[aStart],B[bStart]);
        }
        
        int ia = Math.min(k / 2, m);//代表考察A前面的ia个元素
        int ib = k - ia;//代表考察B前面的ib个元素
        
        int midA = A[ia + aStart - 1];
        int midB = B[ib + bStart - 1];
        
        if(midA == midB){
            return midA;
        }else if(midA < midB){
            return find(k - ia , A, B,aStart + ia, bStart);
        }else{
            return find(k - ib , A, B, aStart,bStart + ib);
        }
    }   
}
