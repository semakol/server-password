package org.semakol.serverpassword.client.gui;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import org.semakol.serverpassword.client.ClientPasswordStore;

/** Sets or clears the one password this client sends to every server that asks for one. */
public class PasswordEntryScreen extends Screen {
    private static final int WIDGET_WIDTH = 220;
    private static final int MIN_LENGTH = 3;
    private static final int LINE_HEIGHT = 10;

    /** Where saving leads — the screen the player was heading for. */
    private final Screen saveTarget;

    /**
     * Where Back leads. Differs from {@link #saveTarget} when this screen replaced the server list on
     * the way in: the player never asked for the server list either, so Back undoes the whole step.
     */
    @Nullable
    private final Screen backTarget;

    private EditBox passwordBox;
    private Button saveButton;
    private MultiLineLabel explanation;
    private int boxTop;
    private boolean revealed;

    @Nullable
    private Component error;

    public PasswordEntryScreen(Screen parent) {
        this(parent, parent);
    }

    public PasswordEntryScreen(Screen saveTarget, @Nullable Screen backTarget) {
        super(Component.translatable("serverpassword.gui.entry.title"));
        this.saveTarget = saveTarget;
        this.backTarget = backTarget;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDGET_WIDTH) / 2;
        int top = this.height / 3 + 10;
        this.boxTop = top;

        // Wrapped rather than one line per key, so the wording is not cut off on a narrow window.
        this.explanation = MultiLineLabel.create(this.font, Math.min(this.width - 40, 300),
                Component.translatable("serverpassword.gui.entry.explain.what"),
                Component.translatable("serverpassword.gui.entry.explain.first"),
                Component.translatable("serverpassword.gui.entry.explain.change"));

        passwordBox = new EditBox(this.font, left, top, WIDGET_WIDTH, 20,
                Component.translatable("serverpassword.gui.entry.password"));
        passwordBox.setMaxLength(64);
        passwordBox.setHint(Component.translatable("serverpassword.gui.entry.password")
                .withStyle(ChatFormatting.DARK_GRAY));
        // Masking is a formatter rather than a separate field so the cursor and paste keep working.
        passwordBox.setFormatter((text, offset) -> FormattedCharSequence.forward(
                revealed ? text : "*".repeat(text.length()), Style.EMPTY));
        passwordBox.setResponder(text -> {
            error = null;
            updateSaveButton();
        });
        // Pre-filled so the screen doubles as "check what my password is" without retyping it.
        String existing = ClientPasswordStore.get();
        if (existing != null) {
            passwordBox.setValue(existing);
        }
        addRenderableWidget(passwordBox);

        addRenderableWidget(Button.builder(Component.translatable("serverpassword.gui.entry.reveal"),
                        button -> {
                            revealed = !revealed;
                            button.setMessage(Component.translatable(revealed
                                    ? "serverpassword.gui.entry.hide"
                                    : "serverpassword.gui.entry.reveal"));
                        })
                .bounds(left, top + 26, WIDGET_WIDTH, 20)
                .build());

        saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("serverpassword.gui.entry.save"), button -> save())
                .bounds(left, this.height - 68, WIDGET_WIDTH, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left, this.height - 44, WIDGET_WIDTH, 20)
                .build());

        setInitialFocus(passwordBox);
        updateSaveButton();
    }

    private void updateSaveButton() {
        if (saveButton != null && passwordBox != null) {
            saveButton.active = passwordBox.getValue().length() >= MIN_LENGTH;
        }
    }

    private void save() {
        String password = passwordBox.getValue();
        if (password.length() < MIN_LENGTH) {
            error = Component.translatable("serverpassword.gui.entry.error.too_short", MIN_LENGTH);
            return;
        }
        ClientPasswordStore.set(password);
        this.minecraft.setScreen(saveTarget);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (saveButton.active) {
                save();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Laid out upwards from the password box so a taller explanation cannot overlap it.
        int explanationBottom = boxTop - 12;
        int explanationTop = explanationBottom - explanation.getLineCount() * LINE_HEIGHT;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, explanationTop - 18, 0xFFFFFF);
        explanation.renderCentered(graphics, this.width / 2, explanationTop, LINE_HEIGHT, 0xAAAAAA);

        graphics.drawCenteredString(this.font,
                Component.translatable("serverpassword.gui.entry.plaintext_warning")
                        .withStyle(ChatFormatting.DARK_GRAY),
                this.width / 2, this.height - 88, 0x808080);
        if (error != null) {
            graphics.drawCenteredString(this.font, error.copy().withStyle(ChatFormatting.RED),
                    this.width / 2, boxTop + 52, 0xFF5555);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(backTarget != null ? backTarget : saveTarget);
    }
}
