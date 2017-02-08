package de.bitsharesmunich.cryptocoincore.base;

import java.util.List;

/**
 * Created by henry on 05/02/2017.
 */

public abstract class CryptoCoinAccount {

    protected String id;
    protected String name;
    protected Coin coin;
    protected AccountSeed seed;

    public CryptoCoinAccount(String id, String name, Coin coin, AccountSeed seed) {
        this.id = id;
        this.name = name;
        this.coin = coin;
        this.seed = seed;
    }


    public AccountSeed getSeed() {
        return this.seed;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Coin getCoin() {
        return coin;
    }

    public void setCoin(Coin coin) {
        this.coin = coin;
    }

    public abstract List<Balance> getBalance();

}
