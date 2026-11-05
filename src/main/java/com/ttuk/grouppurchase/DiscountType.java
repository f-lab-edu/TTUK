package com.ttuk.grouppurchase;

public enum DiscountType {

    RATE{

        @Override
        public boolean isValidValue(int discountValue, int basePrice){
            return discountValue >= MIN_RATE && discountValue <= MAX_RATE;
        }

        @Override
        public int discountPerStep(int basePrice, int discountValue){
            return basePrice * discountValue / PERCENT;
        }

    },

    AMOUNT {
        @Override
        public boolean isValidValue(int discountValue, int basePrice) {
            return discountValue >= MIN_AMOUNT && discountValue < basePrice;
        }

        @Override
        public int discountPerStep(int basePrice, int discountValue) {
            return discountValue;
        }
    };

    private static final int MIN_RATE = 1;
    private static final int MAX_RATE = 50;
    private static final int MIN_AMOUNT = 1;
    private static final int PERCENT = 100;

    public abstract boolean isValidValue(int discountValue, int basePrice);
    public abstract int discountPerStep(int basePrice, int discountValue);

}
