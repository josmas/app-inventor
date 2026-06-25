/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * Object representing a zoom icons.
 *
 * @class
 */
import './events/events_click.js';
import { IFocusableNode } from './interfaces/i_focusable_node.js';
import type { IPositionable } from './interfaces/i_positionable.js';
import type { UiMetrics } from './metrics_manager.js';
import { Rect } from './utils/rect.js';
import type { WorkspaceSvg } from './workspace_svg.js';
/**
 * Base class for an individual zoom control (in, out, reset).
 *
 * @internal
 */
declare abstract class ZoomControl implements IFocusableNode {
    protected workspace: WorkspaceSvg;
    protected group: SVGGElement;
    private pointerDownHandler;
    private id;
    constructor(workspace: WorkspaceSvg, group: SVGGElement);
    getId(): string;
    /**
     * Handles a mouse down event on the zoom in or zoom out buttons on the
     *    workspace.
     *
     * @param amount Amount of zooming. Negative amount values zoom out, and
     *     positive amount values zoom in.
     * @param e A mouse down or keydown event.
     */
    protected zoom(amount: number, e: Event): void;
    /** Fires a zoom control UI event. */
    protected fireZoomEvent(): void;
    getFocusableElement(): SVGGElement;
    getFocusableTree(): WorkspaceSvg;
    onNodeFocus(): void;
    onNodeBlur(): void;
    canBeFocused(): boolean;
    abstract performAction(_e: Event): void;
    dispose(): void;
}
/**
 * Class for a zoom controls.
 */
export declare class ZoomControls implements IPositionable {
    private readonly workspace;
    /**
     * The unique ID for this component that is used to register with the
     * ComponentManager.
     */
    id: string;
    /** The zoom in control. */
    private zoomInControl;
    /** The zoom out control. */
    private zoomOutControl;
    /** The zoom reset control. */
    private zoomResetControl;
    /** Width of the zoom controls. */
    private readonly WIDTH;
    /** Height of each zoom control. */
    private readonly HEIGHT;
    /** Small spacing used between the zoom in and out control, in pixels. */
    private readonly SMALL_SPACING;
    /**
     * Large spacing used between the zoom in and reset control, in pixels.
     */
    private readonly LARGE_SPACING;
    /** Distance between zoom controls and bottom or top edge of workspace. */
    private readonly MARGIN_VERTICAL;
    /** Distance between zoom controls and right or left edge of workspace. */
    private readonly MARGIN_HORIZONTAL;
    /** The SVG group containing the zoom controls. */
    private svgGroup;
    /** Left coordinate of the zoom controls. */
    private left;
    /** Top coordinate of the zoom controls. */
    private top;
    /** Whether this has been initialized. */
    private initialized;
    /** @param workspace The workspace to sit in. */
    constructor(workspace: WorkspaceSvg);
    /**
     * Create the zoom controls.
     *
     * @returns The zoom controls SVG group.
     */
    createDom(): SVGElement;
    /** Initializes the zoom controls. */
    init(): void;
    /**
     * Disposes of this zoom controls.
     * Unlink from all DOM elements to prevent memory leaks.
     */
    dispose(): void;
    /**
     * Returns the bounding rectangle of the UI element in pixel units relative to
     * the Blockly injection div.
     *
     * @returns The UI elements's bounding box. Null if bounding box should be
     *     ignored by other UI elements.
     */
    getBoundingRectangle(): Rect | null;
    /**
     * Positions the zoom controls.
     * It is positioned in the opposite corner to the corner the
     * categories/toolbox starts at.
     *
     * @param metrics The workspace metrics.
     * @param savedPositions List of rectangles that are already on the workspace.
     */
    position(metrics: UiMetrics, savedPositions: Rect[]): void;
    /**
     * Returns the individual zoom control, if any, with the given ID. Used for
     * focus management.
     *
     * @internal
     */
    getControlWithId(id: string): ZoomControl | undefined;
}
export {};
//# sourceMappingURL=zoom_controls.d.ts.map