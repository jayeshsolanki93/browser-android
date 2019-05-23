package com.cliqz.browser.main;

import com.cliqz.browser.annotations.PerActivity;
import com.cliqz.browser.starttab.FavoritesFragment;
import com.cliqz.browser.starttab.HistoryFragment;
import com.cliqz.browser.purchases.PurchaseFragment;
import com.cliqz.browser.purchases.PurchasesManager;
import com.cliqz.browser.vpn.VpnPanel;

import dagger.Subcomponent;

/**
 * @author Ravjit Uppal
 */
@PerActivity
@Subcomponent(modules = {MainActivityModule.class})
public interface FlavoredActivityComponent extends MainActivityComponent {

    void inject(VpnPanel vpnPanel);

    void inject(HistoryFragment historyFragment);

    void inject(FavoritesFragment favoritesFragment);

    void inject(PurchaseFragment purchaseFragment);

    void inject(PurchasesManager purchasesManager);
}
