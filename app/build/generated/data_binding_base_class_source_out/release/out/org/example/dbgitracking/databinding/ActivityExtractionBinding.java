// Generated by view binder compiler. Do not edit!
package org.example.dbgitracking.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;
import org.example.dbgitracking.R;

public final class ActivityExtractionBinding implements ViewBinding {
  @NonNull
  private final ConstraintLayout rootView;

  @NonNull
  public final TextView boxEmptyPlace;

  @NonNull
  public final TextView extractionMethodDescription;

  @NonNull
  public final TextView extractionMethodLabel;

  @NonNull
  public final Spinner extractionMethodSpinner;

  @NonNull
  public final Button flashlightButton;

  @NonNull
  public final Button newBatchButton;

  @NonNull
  public final TextView newExtractionMethodLabel;

  @NonNull
  public final PreviewView previewView;

  @NonNull
  public final Button scanButtonBatch;

  @NonNull
  public final TextView scanButtonBatchLabel;

  @NonNull
  public final Button scanButtonBox;

  @NonNull
  public final TextView scanButtonBoxLabel;

  @NonNull
  public final Button scanButtonExtract;

  @NonNull
  public final TextView scanButtonExtractLabel;

  @NonNull
  public final TextView scanStatus;

  @NonNull
  public final EditText solventVolume;

  @NonNull
  public final TextView solventVolumeLabel;

  private ActivityExtractionBinding(@NonNull ConstraintLayout rootView,
      @NonNull TextView boxEmptyPlace, @NonNull TextView extractionMethodDescription,
      @NonNull TextView extractionMethodLabel, @NonNull Spinner extractionMethodSpinner,
      @NonNull Button flashlightButton, @NonNull Button newBatchButton,
      @NonNull TextView newExtractionMethodLabel, @NonNull PreviewView previewView,
      @NonNull Button scanButtonBatch, @NonNull TextView scanButtonBatchLabel,
      @NonNull Button scanButtonBox, @NonNull TextView scanButtonBoxLabel,
      @NonNull Button scanButtonExtract, @NonNull TextView scanButtonExtractLabel,
      @NonNull TextView scanStatus, @NonNull EditText solventVolume,
      @NonNull TextView solventVolumeLabel) {
    this.rootView = rootView;
    this.boxEmptyPlace = boxEmptyPlace;
    this.extractionMethodDescription = extractionMethodDescription;
    this.extractionMethodLabel = extractionMethodLabel;
    this.extractionMethodSpinner = extractionMethodSpinner;
    this.flashlightButton = flashlightButton;
    this.newBatchButton = newBatchButton;
    this.newExtractionMethodLabel = newExtractionMethodLabel;
    this.previewView = previewView;
    this.scanButtonBatch = scanButtonBatch;
    this.scanButtonBatchLabel = scanButtonBatchLabel;
    this.scanButtonBox = scanButtonBox;
    this.scanButtonBoxLabel = scanButtonBoxLabel;
    this.scanButtonExtract = scanButtonExtract;
    this.scanButtonExtractLabel = scanButtonExtractLabel;
    this.scanStatus = scanStatus;
    this.solventVolume = solventVolume;
    this.solventVolumeLabel = solventVolumeLabel;
  }

  @Override
  @NonNull
  public ConstraintLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ActivityExtractionBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ActivityExtractionBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.activity_extraction, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ActivityExtractionBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.boxEmptyPlace;
      TextView boxEmptyPlace = ViewBindings.findChildViewById(rootView, id);
      if (boxEmptyPlace == null) {
        break missingId;
      }

      id = R.id.extractionMethodDescription;
      TextView extractionMethodDescription = ViewBindings.findChildViewById(rootView, id);
      if (extractionMethodDescription == null) {
        break missingId;
      }

      id = R.id.extractionMethodLabel;
      TextView extractionMethodLabel = ViewBindings.findChildViewById(rootView, id);
      if (extractionMethodLabel == null) {
        break missingId;
      }

      id = R.id.extractionMethodSpinner;
      Spinner extractionMethodSpinner = ViewBindings.findChildViewById(rootView, id);
      if (extractionMethodSpinner == null) {
        break missingId;
      }

      id = R.id.flashlightButton;
      Button flashlightButton = ViewBindings.findChildViewById(rootView, id);
      if (flashlightButton == null) {
        break missingId;
      }

      id = R.id.newBatchButton;
      Button newBatchButton = ViewBindings.findChildViewById(rootView, id);
      if (newBatchButton == null) {
        break missingId;
      }

      id = R.id.newExtractionMethodLabel;
      TextView newExtractionMethodLabel = ViewBindings.findChildViewById(rootView, id);
      if (newExtractionMethodLabel == null) {
        break missingId;
      }

      id = R.id.previewView;
      PreviewView previewView = ViewBindings.findChildViewById(rootView, id);
      if (previewView == null) {
        break missingId;
      }

      id = R.id.scanButtonBatch;
      Button scanButtonBatch = ViewBindings.findChildViewById(rootView, id);
      if (scanButtonBatch == null) {
        break missingId;
      }

      id = R.id.scanButtonBatchLabel;
      TextView scanButtonBatchLabel = ViewBindings.findChildViewById(rootView, id);
      if (scanButtonBatchLabel == null) {
        break missingId;
      }

      id = R.id.scanButtonBox;
      Button scanButtonBox = ViewBindings.findChildViewById(rootView, id);
      if (scanButtonBox == null) {
        break missingId;
      }

      id = R.id.scanButtonBoxLabel;
      TextView scanButtonBoxLabel = ViewBindings.findChildViewById(rootView, id);
      if (scanButtonBoxLabel == null) {
        break missingId;
      }

      id = R.id.scanButtonExtract;
      Button scanButtonExtract = ViewBindings.findChildViewById(rootView, id);
      if (scanButtonExtract == null) {
        break missingId;
      }

      id = R.id.scanButtonExtractLabel;
      TextView scanButtonExtractLabel = ViewBindings.findChildViewById(rootView, id);
      if (scanButtonExtractLabel == null) {
        break missingId;
      }

      id = R.id.scanStatus;
      TextView scanStatus = ViewBindings.findChildViewById(rootView, id);
      if (scanStatus == null) {
        break missingId;
      }

      id = R.id.solventVolume;
      EditText solventVolume = ViewBindings.findChildViewById(rootView, id);
      if (solventVolume == null) {
        break missingId;
      }

      id = R.id.solventVolumeLabel;
      TextView solventVolumeLabel = ViewBindings.findChildViewById(rootView, id);
      if (solventVolumeLabel == null) {
        break missingId;
      }

      return new ActivityExtractionBinding((ConstraintLayout) rootView, boxEmptyPlace,
          extractionMethodDescription, extractionMethodLabel, extractionMethodSpinner,
          flashlightButton, newBatchButton, newExtractionMethodLabel, previewView, scanButtonBatch,
          scanButtonBatchLabel, scanButtonBox, scanButtonBoxLabel, scanButtonExtract,
          scanButtonExtractLabel, scanStatus, solventVolume, solventVolumeLabel);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}