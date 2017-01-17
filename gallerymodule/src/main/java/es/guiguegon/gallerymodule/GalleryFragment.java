package es.guiguegon.gallerymodule;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import es.guiguegon.gallerymodule.adapters.GalleryAdapter;
import es.guiguegon.gallerymodule.helpers.CameraHelper;
import es.guiguegon.gallerymodule.helpers.GalleryHelper;
import es.guiguegon.gallerymodule.helpers.PermissionsManager;
import es.guiguegon.gallerymodule.model.GalleryMedia;
import es.guiguegon.gallerymodule.utils.ScreenUtils;
import java.util.ArrayList;
import java.util.List;

public class GalleryFragment extends Fragment
        implements GalleryAdapter.OnGalleryClickListener, GalleryHelper.GalleryHelperListener {

    private static final String ARGUMENT_MULTISELECTION = "argument_multiselection";
    private static final String ARGUMENT_SHOW_VIDEOS = "argument_show_videos";
    private static final String ARGUMENT_MAX_SELECTED_ITEMS = "argument_max_selected_items";
    private static final String KEY_GALLERY_MEDIA = "key_gallery_media";
    private static final String KEY_SELECTED_POSITION = "key_selected_position";

    private Toolbar toolbar;
    private RecyclerView galleryRecyclerView;
    private ProgressBar loadingProgressBar;
    private Button btnRetry;
    private TextView emptyTextview;

    private ArrayList<GalleryMedia> galleryMedias = new ArrayList<>();
    private ArrayList<Integer> selectedPositions = new ArrayList<>();

    private GalleryAdapter galleryAdapter;
    private StaggeredGridLayoutManager staggeredGridLayoutManager;
    private CameraHelper cameraHelper;
    private GalleryHelper galleryHelper;

    private MenuItem checkItem;
    private Dialog dialog;
    private boolean multiselection;
    private boolean showVideos;
    private int maxSelectedItems;

    static GalleryFragment newInstance(boolean multiselection, int maxSelectedItems,
            boolean showVideos) {
        GalleryFragment fragment = new GalleryFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ARGUMENT_MULTISELECTION, multiselection);
        arguments.putBoolean(ARGUMENT_SHOW_VIDEOS, showVideos);
        arguments.putInt(ARGUMENT_MAX_SELECTED_ITEMS, maxSelectedItems);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            multiselection = getArguments().getBoolean(ARGUMENT_MULTISELECTION, false);
            showVideos = getArguments().getBoolean(ARGUMENT_SHOW_VIDEOS, false);
            maxSelectedItems =
                    getArguments().getInt(ARGUMENT_MAX_SELECTED_ITEMS, Integer.MAX_VALUE);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        galleryHelper = GalleryHelper.getInstance();
        cameraHelper = CameraHelper.getInstance();
        galleryHelper.onCreate(getContext(), this);
        cameraHelper.onCreate(getContext());
        return inflater.inflate(R.layout.fragment_gallery, container, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        galleryHelper.onDestroy();
        cameraHelper.onDestroy();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        galleryRecyclerView = (RecyclerView) view.findViewById(R.id.gallery_recycler_view);
        loadingProgressBar = (ProgressBar) view.findViewById(R.id.loading_progress_bar);
        btnRetry = (Button) view.findViewById(R.id.btn_retry);
        emptyTextview = (TextView) view.findViewById(R.id.empty_textview);
        setupUi();
        if (savedInstanceState == null) {
            init();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(KEY_GALLERY_MEDIA, galleryMedias);
        outState.putIntegerArrayList(KEY_SELECTED_POSITION,
                galleryAdapter.getSelectedItemsPosition());
        outState.putBoolean(ARGUMENT_MULTISELECTION, multiselection);
        outState.putBoolean(ARGUMENT_SHOW_VIDEOS, showVideos);
        outState.putInt(ARGUMENT_MAX_SELECTED_ITEMS, maxSelectedItems);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            galleryMedias = savedInstanceState.getParcelableArrayList(KEY_GALLERY_MEDIA);
            selectedPositions = savedInstanceState.getIntegerArrayList(KEY_SELECTED_POSITION);
            multiselection = savedInstanceState.getBoolean(ARGUMENT_MULTISELECTION);
            showVideos = savedInstanceState.getBoolean(ARGUMENT_SHOW_VIDEOS);
            maxSelectedItems = savedInstanceState.getInt(ARGUMENT_MAX_SELECTED_ITEMS);
            afterConfigChange();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        GalleryMedia galleryMedia =
                cameraHelper.onGetPictureIntentResults(requestCode, resultCode, data);
        if (galleryMedia != null) {
//            onGalleryMedia(galleryMedia);
        }
    }

    protected void setupUi() {
        setToolbar(toolbar);
        int columns = getMaxColumns();
        galleryAdapter = new GalleryAdapter(getContext(), columns);
        staggeredGridLayoutManager =
                new StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL);
        galleryRecyclerView.setLayoutManager(staggeredGridLayoutManager);
        galleryRecyclerView.setAdapter(galleryAdapter);
        galleryAdapter.setMultiselection(multiselection);
        galleryAdapter.setMaxSelectedItems(maxSelectedItems);
        galleryAdapter.setOnGalleryClickListener(this);
        btnRetry.setOnClickListener(this::onButtonRetryClick);
    }

    protected void init() {
        getGalleryMedia();
    }

    protected void afterConfigChange() {
        fillGalleryMedia();
        galleryAdapter.setMultiselection(multiselection);
        galleryAdapter.setMaxSelectedItems(maxSelectedItems);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    public int getMaxColumns() {
        int widthRecyclerViewMediaFiles = ScreenUtils.getScreenWidth(getContext());
        int sizeItemsRecyclerView =
                getResources().getDimensionPixelSize(R.dimen.gallery_item_min_width);
        return widthRecyclerViewMediaFiles / sizeItemsRecyclerView;
    }

    private void fillGalleryMedia() {
        if (!galleryMedias.isEmpty()) {
            galleryAdapter.addGalleryImage(galleryMedias);
            galleryAdapter.setSelectedPositions(selectedPositions);
            hideEmptyList();
        } else {
            showEmptyList();
        }
    }

    private void showLoading() {
        loadingProgressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        loadingProgressBar.setVisibility(View.GONE);
    }

    private void showEmptyList() {
        galleryRecyclerView.setVisibility(View.GONE);
        emptyTextview.setVisibility(View.VISIBLE);
    }

    private void hideEmptyList() {
        galleryRecyclerView.setVisibility(View.VISIBLE);
        emptyTextview.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);
    }

    public void showRetry() {
        btnRetry.setVisibility(View.VISIBLE);
    }

    protected void setToolbar(Toolbar toolbar) {
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        ((AppCompatActivity) getActivity()).getSupportActionBar()
                .setDisplayHomeAsUpEnabled(true);
        ((AppCompatActivity) getActivity()).getSupportActionBar()
                .setHomeButtonEnabled(true);
    }

    @Override
    public void onGalleryClick(GalleryMedia galleryMedia) {
        if (multiselection) {
            handleToolbarState();
        } else {
            onGalleryMediaSelected(galleryMedia);
        }
    }

    @Override
    public void onCameraClick() {
        try {
            PermissionsManager.requestMultiplePermissions((ViewGroup) getView(),
                    () -> camera(getActivity()), Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } catch (Exception e) {
            showError(getString(R.string.gallery_exception_necessary_permissions));
        }
    }

    public void getGalleryMedia() {
        try {
            PermissionsManager.requestMultiplePermissions((ViewGroup) getView(),
                    this::getGalleryImages, this::showRetry,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } catch (Exception e) {
            showError(getString(R.string.gallery_exception_necessary_permissions));
            showRetry();
        }
    }

    public void camera(Activity activity) {
        if (showVideos) {
            dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_gallery);
            Button takePhotoButton = (Button) dialog.findViewById(R.id.gallery_take_photo);
            takePhotoButton.setOnClickListener(v -> {
                dialog.dismiss();
                cameraHelper.dispatchGetPictureIntent(activity);
                dialog = null;
            });
            Button recordVideoButton = (Button) dialog.findViewById(R.id.gallery_record_video);
            recordVideoButton.setOnClickListener(v -> {
                dialog.dismiss();
                cameraHelper.dispatchGetVideoIntent(activity);
                dialog = null;
            });
            dialog.show();
        } else {
            cameraHelper.dispatchGetPictureIntent(activity);
        }
    }

    public void getGalleryImages() {
        galleryHelper.getGalleryAsync(showVideos);
        showLoading();
    }

    public void showError(String message) {
        hideLoading();
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG)
                .show();
    }

    public void onGalleryMedia(GalleryMedia galleryMedia) {
        this.galleryMedias.add(0, galleryMedia);
        galleryAdapter.addGalleryImage(galleryMedia);
        hideEmptyList();
    }

    public void onGalleryMedia(List<GalleryMedia> galleryMedias) {
        this.galleryMedias.addAll(0, galleryMedias);
        galleryAdapter.addGalleryImage(galleryMedias);
        hideEmptyList();
    }

    public void onGalleryMediaSelected(ArrayList<GalleryMedia> galleryMedias) {
        Intent intent = new Intent();
        intent.putParcelableArrayListExtra(GalleryActivity.RESULT_GALLERY_MEDIA_LIST,
                galleryMedias);
        getActivity().setResult(Activity.RESULT_OK, intent);
        getActivity().supportFinishAfterTransition();
    }

    public void onGalleryMediaSelected(GalleryMedia galleryMedia) {
        ArrayList<GalleryMedia> galleryMedias = new ArrayList<>();
        galleryMedias.add(galleryMedia);
        onGalleryMediaSelected(galleryMedias);
    }

    void onButtonRetryClick(View view) {
        init();
    }

    @Override
    public void onGalleryReady(List<GalleryMedia> galleryMedias) {
        hideLoading();
        onGalleryMedia(galleryMedias);
    }

    @Override
    public void onGalleryError() {
        hideLoading();
        showError(getString(R.string.gallery_something_went_wrong));
        showRetry();
    }

    /** Menu */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_gallery, menu);
        checkItem = menu.findItem(R.id.gallery_action_check);
        handleToolbarState();
    }

    private void handleToolbarState() {
        int selectedItemCount = galleryAdapter.getSelectedItemCount();
        if (selectedItemCount > 0) {
            checkItem.setVisible(true);
            toolbar.setTitle(String.format(getString(R.string.gallery_toolbar_title_selected),
                    String.valueOf(selectedItemCount)));
        } else {
            checkItem.setVisible(false);
            toolbar.setTitle(R.string.gallery_toolbar_title);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.gallery_action_check) {
            onGalleryMediaSelected(galleryAdapter.getSelectedItems());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}