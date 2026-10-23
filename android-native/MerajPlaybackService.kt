<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/black">

    <!-- VIDEO SURFACE -->
    <androidx.media3.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:use_controller="false"
        app:resize_mode="fit"
        app:surface_type="surface_view" />

    <!-- CUSTOM CONTROLS OVERLAY -->
    <LinearLayout
        android:id="@+id/controlsLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#40000000"
        android:clickable="true"
        android:focusable="true"
        android:gravity="center"
        android:orientation="vertical">

        <!-- Center Play/Pause & Seek Icons -->
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/btnRewind"
                android:layout_width="50dp"
                android:layout_height="50dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_media_rew"
                app:tint="@android:color/white" />

            <ImageButton
                android:id="@+id/btnPlayPause"
                android:layout_width="72dp"
                android:layout_height="72dp"
                android:layout_marginHorizontal="40dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_media_play"
                app:tint="@android:color/white" />

            <ImageButton
                android:id="@+id/btnForward"
                android:layout_width="50dp"
                android:layout_height="50dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_media_ff"
                app:tint="@android:color/white" />
        </LinearLayout>
    </LinearLayout>

    <!-- BOTTOM CONTROLS -->
    <LinearLayout
        android:id="@+id/bottomControls"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="#80000000"
        android:orientation="vertical"
        android:padding="16dp"
        app:layout_constraintBottom_toBottomOf="parent">

        <SeekBar
            android:id="@+id/seekBar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:progressTint="@android:color/white"
            android:thumbTint="@android:color/white" />

        <RelativeLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp">

            <TextView
                android:id="@+id/tvCurrentTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="00:00"
                android:textColor="@android:color/white"
                android:textSize="14sp" />

            <TextView
                android:id="@+id/tvTotalTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_alignParentEnd="true"
                android:text="00:00"
                android:textColor="@android:color/white"
                android:textSize="14sp" />
        </RelativeLayout>
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
