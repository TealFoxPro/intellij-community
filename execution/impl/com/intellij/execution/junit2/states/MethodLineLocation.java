package com.intellij.execution.junit2.states;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

public class MethodLineLocation extends MethodLocation {
  private final int myLineNumber;

  public MethodLineLocation(final Project project, final PsiMethod method, final Location<PsiClass> classLocation, final int lineNumber) {
    super(project, method, classLocation);
    myLineNumber = lineNumber;
  }

  public OpenFileDescriptor getOpenFileDescriptor() {
    final VirtualFile virtualFile = getContainingClass().getContainingFile().getVirtualFile();
    return new OpenFileDescriptor(getProject(), virtualFile, myLineNumber, 0);
  }
}
