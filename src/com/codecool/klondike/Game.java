package com.codecool.klondike;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.codecool.klondike.Klondike.main;

public class Game extends Pane {

    private List<Card> deck = new ArrayList<>();

    private Pile stockPile;
    private Pile discardPile;
    private List<Pile> foundationPiles = FXCollections.observableArrayList();
    private List<Pile> tableauPiles = FXCollections.observableArrayList();

    private double dragStartX, dragStartY;
    private List<Card> draggedCards = FXCollections.observableArrayList();

    private static double STOCK_GAP = 1;
    private static double FOUNDATION_GAP = 0;
    private static double TABLEAU_GAP = 30;


    private EventHandler<MouseEvent> onMouseClickedHandler = e -> {
        Card card = (Card) e.getSource();
        if (card.getContainingPile().getPileType() == Pile.PileType.STOCK) {
            card.moveToPile(discardPile);
            card.flip();
            card.setMouseTransparent(false);
            System.out.println("Placed " + card + " to the waste.");
        }
    };

    private EventHandler<MouseEvent> stockReverseCardsHandler = e -> {
        refillStockFromDiscard();
    };

    private EventHandler<MouseEvent> onMousePressedHandler = e -> {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
    };

    private EventHandler<MouseEvent> onMouseDraggedHandler = e -> {
        Card card = (Card) e.getSource();
        Pile activePile = card.getContainingPile();
        if (activePile.getPileType() == Pile.PileType.STOCK)
            return;
        if (card.isFaceDown()) return;
        double offsetX = e.getSceneX() - dragStartX;
        double offsetY = e.getSceneY() - dragStartY;

        // Add cards to dragged
        draggedCards.clear();
        ObservableList<Card> activeCards = activePile.getCards();
        int index = activeCards.indexOf(card);

        for (int i = index; i < activeCards.size(); i++) {
            draggedCards.add(activeCards.get(i));
        }

        for (Card draggedCard: draggedCards) {
            draggedCard.getDropShadow().setRadius(20);
            draggedCard.getDropShadow().setOffsetX(10);
            draggedCard.getDropShadow().setOffsetY(10);
            draggedCard.toFront();
            draggedCard.setTranslateX(offsetX);
            draggedCard.setTranslateY(offsetY);
        }
    };

    private EventHandler<MouseEvent> onMouseReleasedHandler = e -> {
        if (draggedCards.isEmpty())
            return;
        Card card = (Card) e.getSource();
        Pile pile = getValidIntersectingPile(card, tableauPiles);

        Pile source = card.getContainingPile();

        if (pile == null) {
            pile = getValidIntersectingPile(card, foundationPiles);
        }

        if (pile != null) {
            ObservableList<Card> cards = source.getCards();

            if ((cards.size() - draggedCards.size() - 1 >= 0) && (source.getPileType() != Pile.PileType.DISCARD)) {
                Card cardToFlip = cards.get(cards.size() - draggedCards.size() - 1);
                if (cardToFlip.isFaceDown()) cardToFlip.flip();
            }

            handleValidMove(card, pile);

        } else {
            draggedCards.forEach(MouseUtil::slideBack);
            draggedCards.clear();
        }
    };

    public boolean isGameWon() {
        for (Pile foundationPile: foundationPiles) {
            if (foundationPile.numOfCards() != 13) return false;
        }
        return true;
    }

    public Game() {
        deck = Card.createNewDeck();
        initButtons();
        initPiles();
        dealCards();
    }

    public void addMouseEventHandlers(Card card) {
        card.setOnMousePressed(onMousePressedHandler);
        card.setOnMouseDragged(onMouseDraggedHandler);
        card.setOnMouseReleased(onMouseReleasedHandler);
        card.setOnMouseClicked(onMouseClickedHandler);
    }

    public void refillStockFromDiscard() {
        Card card = discardPile.getTopCard();
        while (card != null) {
            card.flip();
            card.moveToPile(stockPile);
            card = discardPile.getTopCard();
        }
        System.out.println("Stock refilled from discard pile.");
    }

    public boolean isMoveValid(Card card, Pile destPile) {
        Card destCard = destPile.getTopCard();

        // FOUNDATION
        if (destPile.getPileType().equals(Pile.PileType.FOUNDATION)) {
            if (destPile.isEmpty() && card.getRank() == 1) {
                return true;
            }
            if ((destCard != null) && (destCard.getRank() == card.getRank() - 1) && (destCard.getSuit() == card.getSuit())) {
                return true;
            }
        }

        // TABLEAU
        if (destPile.getPileType().equals(Pile.PileType.TABLEAU)) {
            if (destPile.isEmpty() && card.getRank() == 13) {
                return true;
            }
            if ((destCard != null) && (destCard.getRank() == card.getRank() + 1)) {
                return Card.isOppositeColor(destCard, card);
            }
        }

        return false;
    }

    private Pile getValidIntersectingPile(Card card, List<Pile> piles) {
        Pile result = null;
        for (Pile pile: piles) {
            if (!pile.equals(card.getContainingPile()) &&
                    isOverPile(card, pile) &&
                    isMoveValid(card, pile))
                result = pile;
        }
        return result;
    }

    private boolean isOverPile(Card card, Pile pile) {
        if (pile.isEmpty())
            return card.getBoundsInParent().intersects(pile.getBoundsInParent());
        else
            return card.getBoundsInParent().intersects(pile.getTopCard().getBoundsInParent());
    }

    private void handleValidMove(Card card, Pile destPile) {
        String msg = null;
        if (destPile.isEmpty()) {
            if (destPile.getPileType().equals(Pile.PileType.FOUNDATION))
                msg = String.format("Placed %s to the foundation.", card);
            if (destPile.getPileType().equals(Pile.PileType.TABLEAU))
                msg = String.format("Placed %s to a new pile.", card);
        } else {
            msg = String.format("Placed %s to %s.", card, destPile.getTopCard());
        }
        System.out.println(msg);
        MouseUtil.slideToDest(draggedCards, destPile);
        draggedCards.clear();

        // Check for win
        Platform.runLater(() -> {
            if (isGameWon()) {
                System.out.println("The game has been won!");
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Congratulation!");
                alert.setHeaderText(null);
                alert.setContentText("Congratulation! You won the game!");
                alert.showAndWait();
            }
        });
    }

    private void initButtons() {
        Button restart_btn = new Button();
        restart_btn.setLayoutX(455);
        restart_btn.setLayoutY(50);
        restart_btn.setPrefWidth(130);
        restart_btn.setPrefHeight(50);
        restart_btn.setText("RESTART");
        restart_btn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                System.out.println("Game restarted.");
                restartGame();
            }
        });
        getChildren().add(restart_btn);
    }

    private void restartGame() {
        stockPile.clear();
        for (int i=0; i < 7; i++) {
            tableauPiles.get(i).clear();
        }
        for (int i=0; i < 4; i++) {
            foundationPiles.get(i).clear();
        }
        discardPile.clear();
        foundationPiles.clear();
        tableauPiles.clear();
        deck = Card.createNewDeck();
        getChildren().clear();
        initPiles();
        dealCards();
        initButtons();
    }

    private void initPiles() {
        stockPile = new Pile(Pile.PileType.STOCK, "Stock", STOCK_GAP);
        stockPile.setBlurredBackground();
        stockPile.setLayoutX(95);
        stockPile.setLayoutY(20);
        stockPile.setOnMouseClicked(stockReverseCardsHandler);
        getChildren().add(stockPile);

        discardPile = new Pile(Pile.PileType.DISCARD, "Discard", STOCK_GAP);
        discardPile.setBlurredBackground();
        discardPile.setLayoutX(285);
        discardPile.setLayoutY(20);
        getChildren().add(discardPile);

        for (int i = 0; i < 4; i++) {
            Pile foundationPile = new Pile(Pile.PileType.FOUNDATION, "Foundation " + i, FOUNDATION_GAP);
            foundationPile.setBlurredBackground();
            foundationPile.setLayoutX(610 + i * 180);
            foundationPile.setLayoutY(20);
            foundationPiles.add(foundationPile);
            getChildren().add(foundationPile);
        }
        for (int i = 0; i < 7; i++) {
            Pile tableauPile = new Pile(Pile.PileType.TABLEAU, "Tableau " + i, TABLEAU_GAP);
            tableauPile.setBlurredBackground();
            tableauPile.setLayoutX(95 + i * 180);
            tableauPile.setLayoutY(275);
            tableauPiles.add(tableauPile);
            getChildren().add(tableauPile);
        }
    }

    public void dealCards() {
        Iterator<Card> deckIterator = deck.iterator();
        for (int i = 0; i < 7; i++) {
            for (int j = 1; j <= i + 1; j++) {
                Card card = deckIterator.next();
                if (j == i + 1) {
                    card.flip();
                }
                tableauPiles.get(i).addCard(card);
                addMouseEventHandlers(card);
                getChildren().add(card);
            }
        }
        deckIterator.forEachRemaining(card -> {
            stockPile.addCard(card);
            addMouseEventHandlers(card);
            getChildren().add(card);
        });

    }

    public void setTableBackground(Image tableBackground) {
        setBackground(new Background(new BackgroundImage(tableBackground,
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));
    }

}
