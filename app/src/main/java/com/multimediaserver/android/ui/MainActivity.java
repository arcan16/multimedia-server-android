package com.multimediaserver.android.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;

import com.multimediaserver.android.ui.common.SystemBars;
import com.multimediaserver.android.MultimediaApp;
import com.multimediaserver.android.R;
import com.multimediaserver.android.api.Session;
import com.multimediaserver.android.databinding.ActivityMainBinding;
import com.multimediaserver.android.model.User;
import com.multimediaserver.android.ui.account.AccountFragment;
import com.multimediaserver.android.ui.admin.ContentFragment;
import com.multimediaserver.android.ui.admin.GenresFragment;
import com.multimediaserver.android.ui.admin.MultimediaFragment;
import com.multimediaserver.android.ui.admin.UsersFragment;
import com.multimediaserver.android.ui.catalog.CatalogFragment;
import com.multimediaserver.android.ui.common.SessionActivity;
import com.multimediaserver.android.util.Format;

/**
 * App shell (AppLayout + Sidebar): a navigation drawer with "Catálogo", "Mi cuenta" and, for
 * ADMIN only, the "Administración" section. Each destination is a fragment.
 */
public class MainActivity extends SessionActivity {
    public static final String EXTRA_DESTINATION = "destination";
    public static final String EXTRA_TITLE_ID = "titleId";
    public static final String EXTRA_EDIT_ID = "editId";

    public static final String CATALOG = "catalog";
    public static final String ACCOUNT = "account";
    public static final String CONTENT = "content";
    public static final String MULTIMEDIA = "multimedia";
    public static final String GENRES = "genres";
    public static final String USERS = "users";

    private static final String STATE_DESTINATION = "currentDestination";

    private ActivityMainBinding b;
    private String current = CATALOG;

    /** Opens a destination of the shell from another screen (e.g. "Añadir episodio" in the detail). */
    public static Intent intent(Context context, String destination, @Nullable Long titleId, @Nullable Long editId) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_DESTINATION, destination);
        if (titleId != null) intent.putExtra(EXTRA_TITLE_ID, (long) titleId);
        if (editId != null) intent.putExtra(EXTRA_EDIT_ID, (long) editId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!hasSession) return;
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        SystemBars.fit(this);

        b.toolbar.setNavigationOnClickListener(v -> b.drawer.openDrawer(GravityCompat.START));
        bindUser();
        b.nav.setNavigationItemSelectedListener(this::onNavItem);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (b.drawer.isDrawerOpen(GravityCompat.START)) {
                    b.drawer.closeDrawer(GravityCompat.START);
                } else if (!CATALOG.equals(current)) {
                    navigate(CATALOG, null);
                } else {
                    finish();
                }
            }
        });

        if (savedInstanceState != null) {
            current = savedInstanceState.getString(STATE_DESTINATION, CATALOG);
            markChecked(current);
            setTitle(titleFor(current));
        } else {
            handleIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_DESTINATION, current);
    }

    private void handleIntent(Intent intent) {
        String destination = intent.getStringExtra(EXTRA_DESTINATION);
        Bundle args = new Bundle();
        if (intent.hasExtra(EXTRA_TITLE_ID)) args.putLong(EXTRA_TITLE_ID, intent.getLongExtra(EXTRA_TITLE_ID, 0));
        if (intent.hasExtra(EXTRA_EDIT_ID)) args.putLong(EXTRA_EDIT_ID, intent.getLongExtra(EXTRA_EDIT_ID, 0));
        navigate(destination != null ? destination : CATALOG, args);
    }

    /** Refreshes the drawer header and the admin section (e.g. after editing your own account). */
    public void bindUser() {
        User user = Session.getUser();
        TextView avatar = b.nav.getHeaderView(0).findViewById(R.id.navAvatar);
        TextView username = b.nav.getHeaderView(0).findViewById(R.id.navUsername);
        TextView role = b.nav.getHeaderView(0).findViewById(R.id.navRole);
        avatar.setText(Format.initials(user));
        username.setText(user != null ? user.username : "");
        role.setText(user != null ? user.rol : "");
        b.nav.getMenu().findItem(R.id.navAdminSection).setVisible(Session.isAdmin());
    }

    private boolean onNavItem(MenuItem item) {
        int id = item.getItemId();
        b.drawer.closeDrawer(GravityCompat.START);
        if (id == R.id.navLogout) {
            MultimediaApp.endSession(null);
            return false;
        }
        String destination = CATALOG;
        if (id == R.id.navAccount) destination = ACCOUNT;
        else if (id == R.id.navContent) destination = CONTENT;
        else if (id == R.id.navMultimedia) destination = MULTIMEDIA;
        else if (id == R.id.navGenres) destination = GENRES;
        else if (id == R.id.navUsers) destination = USERS;
        navigate(destination, null);
        return true;
    }

    public void navigate(String destination, @Nullable Bundle args) {
        // Admin destinations are guarded on the client too; the backend is the authority.
        boolean adminOnly = CONTENT.equals(destination) || MULTIMEDIA.equals(destination)
                || GENRES.equals(destination) || USERS.equals(destination);
        if (adminOnly && !Session.isAdmin()) destination = CATALOG;

        Fragment fragment;
        switch (destination) {
            case ACCOUNT:
                fragment = new AccountFragment();
                break;
            case CONTENT:
                fragment = new ContentFragment();
                break;
            case MULTIMEDIA:
                fragment = new MultimediaFragment();
                break;
            case GENRES:
                fragment = new GenresFragment();
                break;
            case USERS:
                fragment = new UsersFragment();
                break;
            default:
                destination = CATALOG;
                fragment = new CatalogFragment();
        }
        if (args != null) fragment.setArguments(args);
        current = destination;
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
        markChecked(destination);
        setTitle(titleFor(destination));
    }

    private void markChecked(String destination) {
        int id = R.id.navCatalog;
        if (ACCOUNT.equals(destination)) id = R.id.navAccount;
        else if (CONTENT.equals(destination)) id = R.id.navContent;
        else if (MULTIMEDIA.equals(destination)) id = R.id.navMultimedia;
        else if (GENRES.equals(destination)) id = R.id.navGenres;
        else if (USERS.equals(destination)) id = R.id.navUsers;
        b.nav.setCheckedItem(id);
    }

    private static String titleFor(String destination) {
        switch (destination) {
            case ACCOUNT:
                return "Mi cuenta";
            case CONTENT:
                return "Contenido";
            case MULTIMEDIA:
                return "Episodios y vídeo";
            case GENRES:
                return "Géneros";
            case USERS:
                return "Usuarios";
            default:
                return "Catálogo";
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        if (b != null) b.toolbar.setTitle(title);
    }
}
